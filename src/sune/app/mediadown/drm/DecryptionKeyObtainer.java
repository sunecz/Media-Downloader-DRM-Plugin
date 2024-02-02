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
import sune.app.mediadown.media.MediaUtils;
import sune.app.mediadown.media.SegmentedMedia;
import sune.app.mediadown.net.Web.Request;
import sune.app.mediadown.util.Metadata;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.Opt.OptCondition;
import sune.app.mediadown.util.Range;

public final class DecryptionKeyObtainer implements DecryptionContext {
	
	private final TrackerManager trackerManager = new TrackerManager();
	private final EventRegistry<EventType> eventRegistry = new EventRegistry<>();
	
	private final Media media;
	private final Path destination;
	private final int keysMaxRetryAttempts;
	private final int waitOnRetryMs;
	
	private final InternalState state = new InternalState();
	private final SyncObject lockPause = new SyncObject();
	
	private Exception exception;
	
	private MediaDecryptionKey keyVideo;
	private MediaDecryptionKey keyAudio;
	
	public DecryptionKeyObtainer(Media media, Path destination, int keysMaxRetryAttempts, int waitOnRetryMs) {
		this.media = Objects.requireNonNull(media);
		this.destination = Objects.requireNonNull(destination);
		this.keysMaxRetryAttempts = checkKeysMaxRetryAttempts(keysMaxRetryAttempts);
		this.waitOnRetryMs = checkWaitOnRetryMs(waitOnRetryMs);
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
	
	private static final OptCondition<Media> conditionIsSegmentedAndNotSubtitles() {
		return OptCondition.ofAll(
			Media::isSegmented,
			Media::isPhysical,
			(m) -> !m.type().is(MediaType.SUBTITLES)
		);
	}
	
	private static final List<Media> segmentedMedia(Media media) {
		return MediaUtils.filterRecursive(media, conditionIsSegmentedAndNotSubtitles());
	}
	
	private final boolean checkState() {
		// Wait for resume, if paused
		if(isPaused()) {
			lockPause.await();
		}
		
		// If already not running, do not continue
		return state.is(TaskStates.RUNNING);
	}
	
	private final MediaProtection extractWidevinePSSH(List<MediaProtection> protections) {
		return protections.stream()
					.filter((p) -> p.type() == MediaProtectionType.DRM_WIDEVINE)
					.filter((p) -> p.contentType().equalsIgnoreCase("pssh"))
					.findFirst().orElse(null);
	}
	
	private final Media protectedMediaOfType(List<Media> mediaSingles, MediaType type) {
		return mediaSingles.stream()
					.filter((m) -> m.type().is(type) && m.metadata().isProtected())
					.findFirst().orElse(null);
	}
	
	private final PSSH extractPSSH(Media media) {
		if(media == null) {
			return null;
		}
		
		MediaProtection protection = extractWidevinePSSH(media.metadata().protections());
		
		if(protection == null) {
			return null;
		}
		
		return new PSSH(protection.content(), protection.keyId());
	}
	
	private final Path downloadTestSegments(FileDownloader downloader, Path output, List<? extends FileSegment> segments,
			int numOfSegments) throws Exception {
		Range<Long> rangeAll = new Range<>(0L, -1L);
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
				int retval = -1;
				
				try(ReadOnlyProcess process = FFmpeg.createAsynchronousProcess((l) -> {})) {
					ConversionCommand command = builder
						.addOptions(Option.ofShort("decryption_key", key.key()))
						.build();
					
					process.execute(command.toString());
					retval = process.waitFor();
				} catch(IOException ex) {
					// Temporary fix: Ignore the IOException that is thrown when the reader
					// of the process is forcibly closed.
					String message = ex.getMessage();
					
					if(message == null
							|| !message.equals("Stream closed")) {
						throw ex; // Propagate
					}
				}
				
				if(retval == 0) {
					return key;
				}
			}
			
			return null;
		} finally {
			NIO.delete(output);
		}
	}
	
	private final MediaDecryptionKey correctDecryptionKey(FileDownloader downloader, Path output,
			List<? extends FileSegment> segments, List<MediaDecryptionKey> keys, String keyId) throws Exception {
		if(keys == null || keys.isEmpty()) {
			// Null indicates failure
			return null;
		}
		
		if(keyId != null && !keyId.isEmpty()) {
			MediaDecryptionKey key = keys.stream()
				.filter((k) -> k.kid().equals(keyId))
				.findFirst().orElse(null);
			
			if(key != null) {
				return key;
			}
		}
		
		int numOfSegments = 2; // Must be at least 2 (init + 1 content segment)
		Path tempOutput = null;
		
		try {
			tempOutput = downloadTestSegments(downloader, output, segments, numOfSegments);
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
	
	private final void setKeys(MediaDecryptionKey keyVideo, MediaDecryptionKey keyAudio) {
		this.keyVideo = keyVideo;
		this.keyAudio = keyAudio;
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
			if(!media.format().isAnyOf(MediaFormat.DASH, MediaFormat.M3U8)) {
				throw new IllegalArgumentException("Only DASH and M3U8 formats are supported");
			}
			
			if(!media.metadata().isProtected()) {
				throw new IllegalArgumentException("Media not protected");
			}
			
			DRMEngine engine = DRMEngines.fromURI(media.metadata().sourceURI());
			
			if(engine == null) {
				throw new IllegalStateException("DRM engine not found");
			}
			
			List<Media> inputMedia = segmentedMedia(media);
			Media video = null;
			Media audio = null;
			
			video = protectedMediaOfType(inputMedia, MediaType.VIDEO);
			
			if(video == null) {
				throw new IllegalStateException("Video must be present");
			}
			
			List<? extends FileSegment> segmentsVideo = null;
			List<? extends FileSegment> segmentsAudio = null;
			Path pathVideo = null;
			Path pathAudio = null;
			
			pathVideo = destination.resolveSibling(destination.getFileName() + ".video.seg");
			segmentsVideo = ((SegmentedMedia) video).segments().segments();
			
			if(segmentsVideo == null) {
				throw new IllegalStateException("Video must be segmented");
			}
			
			audio = protectedMediaOfType(inputMedia, MediaType.AUDIO);
			
			if(audio != null && audio.isPhysical()) {
				pathAudio = destination.resolveSibling(destination.getFileName() + ".audio.seg");
				segmentsAudio = ((SegmentedMedia) audio).segments().segments();
				
				if(segmentsAudio == null) {
					throw new IllegalStateException("Audio must be segmented");
				}
			}
			
			if(!checkState()) return;
			
			DecryptionProcessTracker decryptTracker = new DecryptionProcessTracker();
			trackerManager.tracker(decryptTracker);
			
			decryptTracker.state(DecryptionProcessState.EXTRACT_PSSH);
			PSSH psshVideo = extractPSSH(video);
			PSSH psshAudio = extractPSSH(audio);
			
			if(!checkState()) return;
			
			decryptTracker.state(DecryptionProcessState.OBTAIN_KEYS);
			DRMResolver resolver = engine.createResolver();
			
			if(resolver == null) {
				throw new IllegalStateException("Invalid DRM resolver");
			}
			
			Request request = resolver.createRequest(media);
			
			if(request == null) {
				throw new IllegalStateException("Request cannot be null");
			}
			
			FileDownloader fileDownloader = new FileDownloader(new TrackerManager());
			
			MediaDecryptionKey keyVideo = null;
			MediaDecryptionKey keyAudio = null;
			
			if(psshVideo != null) {
				MediaDecryptionRequest decryptRequest = new MediaDecryptionRequest(psshVideo.content(), request);
				List<MediaDecryptionKey> keys = decryptionKeys(decryptRequest);
				keyVideo = correctDecryptionKey(fileDownloader, pathVideo, segmentsVideo, keys, psshVideo.keyId());
				
				if(keyVideo == null) {
					throw new IllegalStateException("Decryption key for video not found");
				}
				
				if(!checkState()) return;
			}
			
			if(psshAudio != null) {
				MediaDecryptionRequest decryptRequest = new MediaDecryptionRequest(psshAudio.content(), request);
				List<MediaDecryptionKey> keys = decryptionKeys(decryptRequest);
				keyAudio = correctDecryptionKey(fileDownloader, pathAudio, segmentsAudio, keys, psshAudio.keyId());
				
				if(keyAudio == null) {
					throw new IllegalStateException("Decryption key for audio not found");
				}
				
				if(!checkState()) return;
			}
			
			setKeys(keyVideo, keyAudio);
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
		
		if(!state.is(TaskStates.DONE)) {
			state.set(TaskStates.STOPPED);
		}
		
		eventRegistry.call(DecryptionEvent.END, this);
	}
	
	public void pause() throws Exception {
		if(state.is(TaskStates.PAUSED)) {
			return; // Nothing to do
		}
		
		state.unset(TaskStates.RUNNING);
		state.set(TaskStates.PAUSED);
		eventRegistry.call(DecryptionEvent.PAUSE, this);
	}
	
	public void resume() throws Exception {
		if(!state.is(TaskStates.PAUSED)) {
			return; // Nothing to do
		}
		
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
	
	public MediaDecryptionKey keyVideo() {
		return keyVideo;
	}
	
	public MediaDecryptionKey keyAudio() {
		return keyAudio;
	}
}