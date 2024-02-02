package sune.app.mediadown.drm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.InternalState;
import sune.app.mediadown.TaskStates;
import sune.app.mediadown.concurrent.SyncObject;
import sune.app.mediadown.conversion.ConversionMedia;
import sune.app.mediadown.drm.event.DecryptionContext;
import sune.app.mediadown.drm.event.DecryptionEvent;
import sune.app.mediadown.drm.tracker.DecryptionProcessState;
import sune.app.mediadown.drm.tracker.DecryptionProcessTracker;
import sune.app.mediadown.drm.util.AsciiUtils;
import sune.app.mediadown.drm.util.MP4Decrypt;
import sune.app.mediadown.drm.util.MediaDecryptionKey;
import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventRegistry;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.Listener;
import sune.app.mediadown.event.tracker.TrackerEvent;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.event.tracker.WaitTracker;
import sune.app.mediadown.media.MediaType;
import sune.app.mediadown.util.CheckedConsumer;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.ProcessUtils;
import sune.app.mediadown.util.Utils;

public final class Decryptor implements DecryptionContext {
	
	private final TrackerManager trackerManager = new TrackerManager();
	private final EventRegistry<EventType> eventRegistry = new EventRegistry<>();
	
	private final List<ConversionMedia> conversionMedia;
	private final MediaDecryptionKey keyVideo;
	private final MediaDecryptionKey keyAudio;
	
	private final InternalState state = new InternalState();
	private final SyncObject lockPause = new SyncObject();
	
	private ReadOnlyProcess decryptProcess;
	private Exception exception;
	
	public Decryptor(List<ConversionMedia> conversionMedia, MediaDecryptionKey keyVideo, MediaDecryptionKey keyAudio) {
		this.conversionMedia = Objects.requireNonNull(conversionMedia);
		this.keyVideo = keyVideo;
		this.keyAudio = keyAudio;
	}
	
	private final boolean checkState() {
		// Wait for resume, if paused
		if(isPaused()) {
			lockPause.await();
		}
		
		// If already not running, do not continue
		return state.is(TaskStates.RUNNING);
	}
	
	private final ConversionMedia protectedMediaOfType(MediaType type) {
		return conversionMedia.stream()
					.filter((m) -> m.media().type().is(type) && m.media().metadata().isProtected())
					.findFirst().orElse(null);
	}
	
	private final Path asciiTempPath(Path dir, String asciiFileName) throws IOException {
		return AsciiUtils.tempPath(dir.toAbsolutePath(), asciiFileName);
	}
	
	private final void decrypt(Path input, MediaDecryptionKey key) throws Exception {
		// Since mp4decrypt has some problems with non-ascii characters in paths,
		// move files so that we work with only ascii characters temporarily.
		Path tempInput = asciiTempPath(input.getParent(), Utils.randomString(32));
		Path tempOutput = tempInput.resolveSibling(tempInput.getFileName() + ".decrypted");
		NIO.move(input, tempInput);
		
		int retval = -1;
		try {
			decryptProcess = MP4Decrypt.execute(tempInput, tempOutput, key);
			retval = decryptProcess.waitFor();
		} catch(IOException ex) {
			// Temporary fix: Ignore the IOException that is thrown when the reader
			// of the process is forcibly closed.
			String message = ex.getMessage();
			
			if(message == null
					|| !message.equals("Stream closed")) {
				throw ex; // Propagate
			}
		}
		
		if(retval != 0) {
			throw new IllegalStateException("Decryption eneded unsucessfully");
		}
		
		// Clean up the temporary files and replace the encrypted input file with
		// the new decrypted one.
		NIO.move(tempOutput, input);
		NIO.delete(tempInput);
		AsciiUtils.maybeDeleteTempDirectories();
	}
	
	private final void processAction(CheckedConsumer<Process> action) throws Exception {
		Process process;
		
		// Check the chain of values to avoid NPE
		if(decryptProcess == null
				|| (process = decryptProcess.process()) == null) {
			return;
		}
		
		action.accept(process);
	}
	
	public void start() throws Exception {
		if(state.is(TaskStates.STARTED) && state.is(TaskStates.RUNNING)) {
			return; // Nothing to do
		}
		
		state.clear(TaskStates.STARTED | TaskStates.RUNNING);
		
		// Translate the Tracker events so that the updates are propagated correctly.
		trackerManager.addEventListener(
			TrackerEvent.UPDATE,
			(p) -> eventRegistry.call(DecryptionEvent.UPDATE, this)
		);
		
		trackerManager.tracker(new WaitTracker());
		eventRegistry.call(DecryptionEvent.BEGIN, this);
		
		try {
			DecryptionProcessTracker decryptTracker = new DecryptionProcessTracker();
			trackerManager.tracker(decryptTracker);
			
			ConversionMedia video = protectedMediaOfType(MediaType.VIDEO);
			ConversionMedia audio = protectedMediaOfType(MediaType.AUDIO);
			
			if(video == null || audio == null) {
				throw new IllegalStateException("Both video and audio must be present");
			}
			
			Path pathVideo = video.path();
			Path pathAudio = audio.path();
			
			if(keyVideo != null) {
				decryptTracker.state(DecryptionProcessState.DECRYPT_VIDEO);
				decrypt(pathVideo, keyVideo);
				
				if(!checkState()) return;
			}
			
			if(keyAudio != null) {
				decryptTracker.state(DecryptionProcessState.DECRYPT_AUDIO);
				decrypt(pathAudio, keyAudio);
				
				if(!checkState()) return;
			}
			
			state.set(TaskStates.DONE);
		} catch(Exception ex) {
			exception = ex;
			state.set(TaskStates.ERROR);
			eventRegistry.call(DecryptionEvent.ERROR, this);
			throw ex; // Forward the exception
		} finally {
			stop();
		}
	}
	
	public void stop() throws Exception {
		if(state.is(TaskStates.STOPPED)) {
			return; // Nothing to do
		}
		
		state.unset(TaskStates.RUNNING);
		state.unset(TaskStates.PAUSED);
		lockPause.unlock();
		
		processAction(Process::destroyForcibly);
		
		if(!state.is(TaskStates.DONE)) {
			state.set(TaskStates.STOPPED);
		}
		
		eventRegistry.call(DecryptionEvent.END, this);
	}
	
	public void pause() throws Exception {
		if(state.is(TaskStates.PAUSED)) {
			return; // Nothing to do
		}
		
		processAction(ProcessUtils::pause);
		
		state.unset(TaskStates.RUNNING);
		state.set(TaskStates.PAUSED);
		eventRegistry.call(DecryptionEvent.PAUSE, this);
	}
	
	public void resume() throws Exception {
		if(!state.is(TaskStates.PAUSED)) {
			return; // Nothing to do
		}
		
		processAction(ProcessUtils::resume);
		
		state.unset(TaskStates.PAUSED);
		state.set(TaskStates.RUNNING);
		lockPause.unlock();
		eventRegistry.call(DecryptionEvent.RESUME, this);
	}
	
	@Override
	public boolean isRunning() {
		return state.is(TaskStates.RUNNING);
	}
	
	@Override
	public boolean isStarted() {
		return state.is(TaskStates.STARTED);
	}
	
	@Override
	public boolean isDone() {
		return state.is(TaskStates.DONE);
	}
	
	@Override
	public boolean isPaused() {
		return state.is(TaskStates.PAUSED);
	}
	
	@Override
	public boolean isStopped() {
		return state.is(TaskStates.STOPPED);
	}
	
	@Override
	public boolean isError() {
		return state.is(TaskStates.ERROR);
	}
	
	@Override
	public <V> void addEventListener(Event<? extends DecryptionEvent, V> event, Listener<V> listener) {
		eventRegistry.add(event, listener);
	}
	
	@Override
	public <V> void removeEventListener(Event<? extends DecryptionEvent, V> event, Listener<V> listener) {
		eventRegistry.remove(event, listener);
	}
	
	@Override
	public TrackerManager trackerManager() {
		return trackerManager;
	}
	
	@Override
	public Exception exception() {
		return exception;
	}
}