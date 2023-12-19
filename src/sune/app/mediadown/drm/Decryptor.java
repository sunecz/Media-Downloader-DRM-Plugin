package sune.app.mediadown.drm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.InternalState;
import sune.app.mediadown.TaskStates;
import sune.app.mediadown.concurrent.SyncObject;
import sune.app.mediadown.conversion.ConversionCommand;
import sune.app.mediadown.conversion.ConversionCommand.Input;
import sune.app.mediadown.conversion.ConversionCommand.Option;
import sune.app.mediadown.conversion.ConversionCommand.Output;
import sune.app.mediadown.download.DownloadConfiguration;
import sune.app.mediadown.download.FileDownloader;
import sune.app.mediadown.download.segment.FileSegment;
import sune.app.mediadown.drm.event.DecryptionContext;
import sune.app.mediadown.drm.event.DecryptionEvent;
import sune.app.mediadown.drm.tracker.DecryptionProcessState;
import sune.app.mediadown.drm.tracker.DecryptionProcessTracker;
import sune.app.mediadown.drm.util.AsciiUtils;
import sune.app.mediadown.drm.util.MP4Decrypt;
import sune.app.mediadown.drm.util.MediaDecryptionKey;
import sune.app.mediadown.drm.util.MediaDecryptionRequest;
import sune.app.mediadown.drm.util.PSSH;
import sune.app.mediadown.drm.util.WV;
import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventRegistry;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.Listener;
import sune.app.mediadown.event.tracker.TrackerEvent;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.event.tracker.WaitTracker;
import sune.app.mediadown.ffmpeg.FFmpeg;
import sune.app.mediadown.media.Media;
import sune.app.mediadown.media.MediaFormat;
import sune.app.mediadown.media.MediaProtection;
import sune.app.mediadown.media.MediaProtectionType;
import sune.app.mediadown.media.MediaType;
import sune.app.mediadown.media.SegmentedMedia;
import sune.app.mediadown.net.Web.Request;
import sune.app.mediadown.util.CheckedConsumer;
import sune.app.mediadown.util.Metadata;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.ProcessUtils;
import sune.app.mediadown.util.Range;
import sune.app.mediadown.util.Utils;

public final class Decryptor implements DecryptionContext {
	
	private final TrackerManager trackerManager = new TrackerManager();
	private final EventRegistry<EventType> eventRegistry = new EventRegistry<>();
	
	private final Media rootMedia;
	private final List<Media> inputMedia;
	private final List<Path> inputPaths;
	private final int keysMaxRetryAttempts;
	private final int waitOnRetryMs;
	
	private final InternalState state = new InternalState();
	private final SyncObject lockPause = new SyncObject();
	
	private ReadOnlyProcess decryptProcess;
	private Exception exception;
	
	public Decryptor(Media rootMedia, List<Media> inputMedia, List<Path> inputPaths, int keysMaxRetryAttempts,
			int waitOnRetryMs) {
		this.rootMedia = Objects.requireNonNull(rootMedia);
		this.inputMedia = Objects.requireNonNull(inputMedia);
		this.inputPaths = Objects.requireNonNull(inputPaths);
		this.keysMaxRetryAttempts = checkKeysMaxRetryAttempts(keysMaxRetryAttempts);
		this.waitOnRetryMs = checkWaitOnRetryMs(waitOnRetryMs);
		trackerManager.tracker(new WaitTracker());
	}
	
	private static final int checkKeysMaxRetryAttempts(int value) {
		if(value < 0) {
			throw new IllegalArgumentException("keysMaxRetryAttempts must be >= 0");
		}
		
		return value;
	}
	
	private static final int checkWaitOnRetryMs(int value) {
		if(value < 0) {
			throw new IllegalArgumentException("waitOnRetryMs must be >= 0");
		}
		
		return value;
	}
	
	private final boolean checkState() {
		// Wait for resume, if paused
		if(isPaused()) {
			lockPause.await();
		}
		
		// If already not running, do not continue
		return state.is(TaskStates.RUNNING);
	}
	
	private final String extractWidevinePSSH(List<MediaProtection> protections) {
		return protections.stream()
					.filter((p) -> p.type() == MediaProtectionType.DRM_WIDEVINE)
					.filter((p) -> p.contentType().equalsIgnoreCase("pssh"))
					.map(MediaProtection::content)
					.findFirst().orElse(null);
	}
	
	private final Media protectedMediaOfType(List<Media> mediaSingles, MediaType type) {
		return mediaSingles.stream()
					.filter((m) -> m.type().is(type) && m.metadata().isProtected())
					.findFirst().orElse(null);
	}
	
	private final PSSH extractPSSH(Media media) throws Exception {
		if(media.format() != MediaFormat.DASH) {
			throw new IllegalArgumentException("Only DASH is supported so far");
		}
		
		if(!media.metadata().isProtected()) {
			throw new IllegalArgumentException("Media not protected");
		}
		
		String valueVideo = null;
		String valueAudio = null;
		
		Media video = protectedMediaOfType(inputMedia, MediaType.VIDEO);
		Media audio = protectedMediaOfType(inputMedia, MediaType.AUDIO);
		
		if(video != null) {
			valueVideo = extractWidevinePSSH(video.metadata().protections());
		}
		
		if(audio != null) {
			valueAudio = extractWidevinePSSH(audio.metadata().protections());
		}
		
		return new PSSH(valueVideo, valueAudio);
	}
	
	private final Path downloadTestSegments(FileDownloader downloader, Path input, List<FileSegment> segments,
			int numOfSegments) throws Exception {
		Range<Long> rangeAll = new Range<>(0L, -1L);
		Path output = input.resolveSibling(input.getFileName() + ".seg");
		long offset = 0L;
		
		for(int i = 0; i < numOfSegments; ++i) {
			long downloaded = downloader.start(
				Request.of(segments.get(i).uri()).retry(10).GET(),
				output,
				DownloadConfiguration.ofRanges(new Range<>(offset, -1L), rangeAll)
			);
			offset += downloaded;
		}
		
		return output;
	}
	
	private final MediaDecryptionKey filterDecryptionKey(Path input, List<MediaDecryptionKey> keys)
			throws Exception {
		Metadata metadataInput = Metadata.of("noExplicitFormat", true);
		Path output = NIO.tempFile(input.getFileName().toString(), ".dec");
		
		ConversionCommand.Builder builder = FFmpeg.Command.builder()
			.addInputs(Input.of(input, MediaFormat.MP4, metadataInput))
			.addOutputs(Output.of(output, MediaFormat.MP4))
			.addOptions(FFmpeg.Options.yes());
		
		try {
			for(MediaDecryptionKey key : keys) {
				// Use FFmpeg decryption_key flag to check whether a initial segment and the next
				// segment together can be decrypted using the specific key. If it fails the key
				// is not the correct one and the FFmpeg will return a non-zero exit code, otherwise
				// the key is correct and we can return it. Note that this method returns just one
				// key, so media with multiple decryption keys are not supported.
				try(ReadOnlyProcess process = FFmpeg.createAsynchronousProcess((l) -> {})) {
					ConversionCommand command = builder
						.addOptions(Option.ofShort("decryption_key", key.key()))
						.build();
					
					process.execute(command.toString());
					int retval = process.waitFor();
					
					if(retval == 0) {
						return key;
					}
				}
			}
			
			return null;
		} finally {
			NIO.delete(output);
		}
	}
	
	private final MediaDecryptionKey correctDecryptionKey(FileDownloader downloader, Path input,
			List<FileSegment> segments, List<MediaDecryptionKey> keys) throws Exception {
		if(keys == null || keys.isEmpty()) {
			// Null indicates failure
			return null;
		}
		
		int numOfSegments = 2; // Must be at least 2 (init + 1 content segment)
		Path tempOutput = null;
		
		try {
			tempOutput = downloadTestSegments(downloader, input, segments, numOfSegments);
			return filterDecryptionKey(tempOutput, keys);
		} finally {
			if(tempOutput != null) {
				NIO.delete(tempOutput);
			}
		}
	}
	
	private final void waitRetry(int attempt) throws InterruptedException {
		int waitMs = (int) (waitOnRetryMs * Math.pow(attempt, 4.0 / 3.0));
		Thread.sleep(waitMs); // Simple wait
	}
	
	private final List<MediaDecryptionKey> decryptionKeys(MediaDecryptionRequest request) throws Exception {
		int attempt = 0;
		
		do {
			List<MediaDecryptionKey> keys = WV.decryptionKeys(request);
			
			if(keys != null && !keys.isEmpty()) {
				return keys;
			}
			
			waitRetry(attempt + 1); // Wait a little
		} while(++attempt <= keysMaxRetryAttempts);
		
		return null;
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
		
		decryptProcess = MP4Decrypt.execute(tempInput, tempOutput, key);
		int retval = decryptProcess.waitFor();
		
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
		
		state.set(TaskStates.RUNNING);
		state.set(TaskStates.STARTED);
		
		// Translate the Tracker events so that the updates are propagated correctly.
		trackerManager.addEventListener(
			TrackerEvent.UPDATE,
			(p) -> eventRegistry.call(DecryptionEvent.UPDATE, this)
		);
		
		eventRegistry.call(DecryptionEvent.BEGIN, this);
		
		try {
			List<FileSegment> segmentsVideo = null;
			List<FileSegment> segmentsAudio = null;
			Path pathVideo = null;
			Path pathAudio = null;
			
			for(int i = 0, l = inputMedia.size(); i < l; ++i) {
				Path tempFile = inputPaths.get(i);
				Media media = inputMedia.get(i);
				MediaType type = media.type();
				
				if(type.is(MediaType.VIDEO)) {
					pathVideo = tempFile;
					segmentsVideo = Utils.cast(((SegmentedMedia) media).segments().segments());
				} else if(type.is(MediaType.AUDIO)) {
					pathAudio = tempFile;
					segmentsAudio = Utils.cast(((SegmentedMedia) media).segments().segments());
				}
			}
			
			if(pathVideo == null || pathAudio == null) {
				throw new IllegalStateException("Both video and audio must be available");
			}
			
			if(segmentsVideo == null || segmentsAudio == null) {
				throw new IllegalStateException("Only segmentable video and audio supported");
			}
			
			DRMEngine engine = DRMEngines.fromURI(rootMedia.metadata().sourceURI());
			
			if(engine == null) {
				throw new IllegalStateException("DRM engine not found");
			}
			
			if(!checkState()) return;
			
			DecryptionProcessTracker decryptTracker = new DecryptionProcessTracker();
			trackerManager.tracker(decryptTracker);
			
			decryptTracker.state(DecryptionProcessState.EXTRACT_PSSH);
			PSSH pssh = extractPSSH(rootMedia);
			
			if(!checkState()) return;
			
			decryptTracker.state(DecryptionProcessState.OBTAIN_KEYS);
			DRMResolver resolver = engine.createResolver();
			
			if(resolver == null) {
				throw new IllegalStateException("Invalid DRM resolver");
			}
			
			Request request = resolver.createRequest(rootMedia);
			
			if(request == null) {
				throw new IllegalStateException("Request cannot be null");
			}
			
			FileDownloader fileDownloader = new FileDownloader(new TrackerManager());
			
			MediaDecryptionKey keyVideo = null;
			MediaDecryptionKey keyAudio = null;
			String psshValue;
			
			if((psshValue = pssh.video()) != null) {
				MediaDecryptionRequest decryptRequest = new MediaDecryptionRequest(psshValue, request);
				List<MediaDecryptionKey> keys = decryptionKeys(decryptRequest);
				keyVideo = correctDecryptionKey(fileDownloader, pathVideo, segmentsVideo, keys);
				
				if(keyVideo == null) {
					throw new IllegalStateException("Decryption key for video not found");
				}
				
				if(!checkState()) return;
			}
			
			if((psshValue = pssh.audio()) != null) {
				MediaDecryptionRequest decryptRequest = new MediaDecryptionRequest(psshValue, request);
				List<MediaDecryptionKey> keys = decryptionKeys(decryptRequest);
				keyAudio = correctDecryptionKey(fileDownloader, pathAudio, segmentsAudio, keys);
				
				if(keyAudio == null) {
					throw new IllegalStateException("Decryption key for audio not found");
				}
				
				if(!checkState()) return;
			}
			
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