package sune.app.mediadown.drm;

import static sune.app.mediadown.drm.util.Common.logDebug;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
import sune.app.mediadown.drm.util.Common;
import sune.app.mediadown.drm.util.Common.ProcessListener;
import sune.app.mediadown.drm.util.MediaDecryptionKey;
import sune.app.mediadown.drm.util.PSSH;
import sune.app.mediadown.drm.util.WV;
import sune.app.mediadown.drm.util.WV.API.LicenseKey;
import sune.app.mediadown.drm.util.WV.API.LicenseRequest;
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
import sune.app.mediadown.net.Web;
import sune.app.mediadown.net.Web.Request;
import sune.app.mediadown.net.Web.Response;
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
	
	private static final FFmpeg.Command.Builder copyCommandBuilder(
			ConversionCommand.Builder builder
	) {
		// Workaround since there is currently no FFmpeg.Command.Builder::copy method
		return FFmpeg.Command.builder((FFmpeg.Command) builder.build());
	}
	
	private final MediaDecryptionKey filterDecryptionKey(Path input, List<MediaDecryptionKey> keys)
			throws Exception {
		Metadata metadataInput = Metadata.of("noExplicitFormat", true);
		Path output = NIO.tempFile(input.getFileName().toString(), ".dec");
		
		ConversionCommand.Builder builder = FFmpeg.Command.builder()
			.addInputs(Input.of(input, MediaFormat.MP4, metadataInput))
			.addOutputs(Output.of(output, MediaFormat.MP4))
			.addOptions(FFmpeg.Options.yes(), FFmpeg.Options.hideBanner())
			.addOptions(Option.ofShort("xerror")); // Fail immediately
		
		try {
			for(MediaDecryptionKey key : keys) {
				// Use FFmpeg decryption_key flag to check whether a initial segment and the next
				// segment together can be decrypted using the specific key. If it fails the key
				// is not the correct one and the FFmpeg will return a non-zero exit code, otherwise
				// the key is correct and we can return it. Note that this method returns just one
				// key, so media with multiple decryption keys are not supported.
				int retval = -1;
				
				logDebug("Trying key: <%s:%s>", key.kid(), key.key());
				
				try(
					ProcessListener listener = Common.newProcessListener("ffmpeg");
					ReadOnlyProcess process = FFmpeg.createAsynchronousProcess(listener)
				) {
					ConversionCommand command = copyCommandBuilder(builder)
						.addOptions(Option.ofShort("decryption_key", key.key()))
						.build();
					
					String cmd = command.toString();
					logDebug("ffmpeg %s", cmd);
					
					process.execute(cmd);
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
				
				logDebug("Exit code: %d", retval);
				
				if(retval == 0) {
					return key;
				}
			}
			
			return null;
		} finally {
			NIO.delete(output);
		}
	}
	
	private final MediaDecryptionKey correctDecryptionKey(Path output,
			List<? extends FileSegment> segments, List<MediaDecryptionKey> keys, String keyId) throws Exception {
		if(keys == null || keys.isEmpty()) {
			// Null indicates failure
			return null;
		}
		
		logDebug("Select correct decryption key");
		
		boolean isKeyIdPresent = keyId != null && !keyId.isEmpty();
		
		if(isKeyIdPresent) {
			logDebug(
				"Key ID (KID) is present (%s), select the corresponding decryption key",
				keyId
			);
			
			MediaDecryptionKey key = keys.stream()
				.filter((k) -> k.kid().equals(keyId))
				.findFirst().orElse(null);
			
			if(key != null) {
				return key;
			}
			
			logDebug("Corresponding decryption key not found");
		} else {
			logDebug("Key ID (KID) is not present");
		}
		
		logDebug("Find the correct decryption key");
		
		int numOfSegments = 2; // Must be at least 2 (init + 1 content segment)
		Path tempOutput = null;
		
		try(FileDownloader downloader = new FileDownloader(new TrackerManager())) {
			tempOutput = downloadTestSegments(downloader, output, segments, numOfSegments);
			MediaDecryptionKey foundKey = filterDecryptionKey(tempOutput, keys);
			
			if(foundKey == null || isKeyIdPresent) {
				return foundKey;
			}
			
			// If no KID is present, default to the first stream
			return new MediaDecryptionKey("1", foundKey.key());
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
	
	private final List<MediaDecryptionKey> obtainDecryptionKeys(DRMResolver resolver, Media media, String pssh)
			throws Exception {
		LicenseRequest licenseRequest = WV.API.generateLicenseRequest(pssh);
		
		if(!licenseRequest.isValid()) {
			throw new IllegalStateException("Invalid license request");
		}
		
		Request request = resolver.createRequest(media, licenseRequest.request());
		
		if(request == null) {
			throw new IllegalStateException("DRM request cannot be null");
		}
		
		List<LicenseKey> licenseKeys;
		try(Response.OfStream licenseResponse = Web.requestStream(request)) {
			licenseKeys = WV.API.extractLicenseKeys(
				licenseRequest.id(),
				licenseResponse.stream().readAllBytes()
			);
		}
		
		if(licenseKeys == null || licenseKeys.isEmpty()) {
			// Do not throw an exception here, but allow a retry.
			return null;
		}
		
		return licenseKeys.stream()
			.filter((k) -> k.type().equals("CONTENT"))
			.map((k) -> new MediaDecryptionKey(k.kid(), k.key()))
			.collect(Collectors.toList());
	}
	
	private final List<MediaDecryptionKey> decryptionKeys(DRMResolver resolver, Media media, String pssh)
			throws Exception {
		int attempt = 0;
		
		do {
			List<MediaDecryptionKey> keys = obtainDecryptionKeys(resolver, media, pssh);
			
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
			
			MediaDecryptionKey keyVideo = null;
			MediaDecryptionKey keyAudio = null;
			
			if(psshVideo != null) {
				logDebug("Video has PSSH, get its decryption keys");
				
				List<MediaDecryptionKey> keys = decryptionKeys(resolver, video, psshVideo.content());
				
				logDebug(
					"Video decryption keys:\n<\n%s\n>",
					keys.stream().map((k) -> k.kid() + ':' + k.key()).collect(Collectors.joining("\n"))
				);
				
				keyVideo = correctDecryptionKey(pathVideo, segmentsVideo, keys, psshVideo.keyId());
				
				if(keyVideo == null) {
					logDebug("Video decryption key not found");
					throw new IllegalStateException("Decryption key for video not found");
				}
				
				logDebug(
					"Found video decryption key: <%s:%s>",
					keyVideo.kid(), keyVideo.key()
				);
				
				if(!checkState()) return;
			}
			
			if(psshAudio != null) {
				logDebug("Audio has PSSH, get its decryption keys");
				
				List<MediaDecryptionKey> keys = decryptionKeys(resolver, audio, psshAudio.content());
				
				logDebug(
					"Audio decryption keys: <\n%s\n>",
					keys.stream().map((k) -> k.kid() + ':' + k.key()).collect(Collectors.joining("\n"))
				);
				
				keyAudio = correctDecryptionKey(pathAudio, segmentsAudio, keys, psshAudio.keyId());
				
				if(keyAudio == null) {
					logDebug("Audio decryption key not found");
					throw new IllegalStateException("Decryption key for audio not found");
				}
				
				logDebug(
					"Found audio decryption key: <%s:%s>",
					keyAudio.kid(), keyAudio.key()
				);
				
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