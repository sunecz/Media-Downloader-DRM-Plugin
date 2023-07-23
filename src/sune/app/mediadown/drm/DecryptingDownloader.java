package sune.app.mediadown.drm;

import java.lang.reflect.InvocationTargetException;
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
import sune.app.mediadown.download.Download;
import sune.app.mediadown.download.DownloadConfiguration;
import sune.app.mediadown.download.DownloadResult;
import sune.app.mediadown.download.FileDownloader;
import sune.app.mediadown.download.InternalDownloader;
import sune.app.mediadown.download.MediaDownloadConfiguration;
import sune.app.mediadown.download.segment.FileSegment;
import sune.app.mediadown.drm.tracker.DecryptionProcessState;
import sune.app.mediadown.drm.tracker.DecryptionProcessTracker;
import sune.app.mediadown.drm.util.MP4Decrypt;
import sune.app.mediadown.drm.util.MediaDecryptionKey;
import sune.app.mediadown.drm.util.MediaDecryptionRequest;
import sune.app.mediadown.drm.util.WV;
import sune.app.mediadown.entity.Downloader;
import sune.app.mediadown.entity.Downloaders;
import sune.app.mediadown.event.DownloadEvent;
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
import sune.app.mediadown.media.format.MPD;
import sune.app.mediadown.media.format.MPD.MPDCombinedFile;
import sune.app.mediadown.net.Web.Request;
import sune.app.mediadown.pipeline.DownloadPipelineResult;
import sune.app.mediadown.util.Metadata;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Range;
import sune.app.mediadown.util.Reflection3;
import sune.app.mediadown.util.Utils;

public final class DecryptingDownloader implements Download, DownloadResult {
	
	private final TrackerManager manager = new TrackerManager();
	private final EventRegistry<EventType> eventRegistry = new EventRegistry<>();
	
	private final Media media;
	private final Path dest;
	private final MediaDownloadConfiguration configuration;
	
	private final InternalState state = new InternalState();
	private final SyncObject lockPause = new SyncObject();
	
	private DownloadPipelineResult pipelineResult;
	private Download download;
	private InternalDownloader downloader;
	
	DecryptingDownloader(Media media, Path dest, MediaDownloadConfiguration configuration) {
		this.media         = Objects.requireNonNull(media);
		this.dest          = Objects.requireNonNull(dest);
		this.configuration = Objects.requireNonNull(configuration);
		manager.tracker(new WaitTracker());
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
	
	private final PSSH extractPSSH(Media media) throws Exception {
		if(media.format() != MediaFormat.DASH) {
			throw new IllegalArgumentException("Only DASH is supported so far");
		}
		
		if(!media.metadata().isProtected()) {
			throw new IllegalArgumentException("Media not protected");
		}
		
		Request request = Request.of(media.uri()).GET();
		
		for(MPDCombinedFile result : MPD.reduce(MPD.parse(request))) {
			MPD.ContentProtection protectionVideo = result.video().protection();
			MPD.ContentProtection protectionAudio = result.audio().protection();
			String valueVideo = null;
			String valueAudio = null;
			
			if(protectionVideo.isPresent()) {
				valueVideo = extractWidevinePSSH(protectionVideo.protections());
			}
			
			if(protectionAudio.isPresent()) {
				valueAudio = extractWidevinePSSH(protectionAudio.protections());
			}
			
			return new PSSH(valueVideo, valueAudio);
		}
		
		return null;
	}
	
	private final Path downloadTestSegments(FileDownloader downloader, Path input, List<FileSegment> segments,
			int numOfSegments) throws Exception {
		Range<Long> rangeAll = new Range<>(0L, -1L);
		Path output = input.resolveSibling(input.getFileName() + ".seg");
		long offset = 0L;
		
		for(int i = 0; i < numOfSegments; ++i) {
			long downloaded = downloader.start(
				Request.of(segments.get(i).uri()).GET(),
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
		if(keys.isEmpty()) {
			// Null indicates failure
			return null;
		}
		
		if(keys.size() == 1) {
			// Only one key, nothing to try, just return it
			return keys.get(0);
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
	
	private final void decrypt(Path input, MediaDecryptionKey key) throws Exception {
		// Since mp4decrypt has some problems with non-ascii characters in file names,
		// move files so that we work with only ascii characters temporarily.
		Path tempInput = input.resolveSibling(Utils.randomString(32));
		Path tempOutput = tempInput.resolveSibling(tempInput.getFileName() + ".decrypted");
		NIO.move_force(input, tempInput);
		
		int retval = MP4Decrypt.decrypt(tempInput, tempOutput, key);
		
		if(retval != 0) {
			throw new IllegalStateException("Decryption eneded unsucessfully");
		}
		
		// Clean up the temporary files and replace the encrypted input file with
		// the new decrypted one.
		NIO.move_force(tempOutput, input);
		NIO.delete(tempInput);
	}
	
	@Override
	public final void start() throws Exception {
		if(state.is(TaskStates.STARTED) && state.is(TaskStates.RUNNING)) {
			return; // Nothing to do
		}
		
		state.set(TaskStates.RUNNING);
		state.set(TaskStates.STARTED);
		
		manager.addEventListener(
			TrackerEvent.UPDATE,
			(p) -> eventRegistry.call(DownloadEvent.UPDATE, new Pair<>(downloader, manager))
		);
		
		Downloader downloaderInstance = Downloaders.get("wms");
		DownloadResult result = downloaderInstance.download(media, dest, configuration);
		download = result.download();
		eventRegistry.bindAll(download, DownloadEvent.UPDATE);
		
		WMSDelegator delegator = new WMSDelegator(download);
		downloader = delegator.ensureInternalDownloader();
		
		eventRegistry.call(DownloadEvent.BEGIN, downloader);
		
		List<Media> mediaSingles = delegator.mediaSegmentedSingles(media);
		List<Path> tempFiles = delegator.temporaryFiles(mediaSingles.size());
		
		List<FileSegment> segmentsVideo = null;
		List<FileSegment> segmentsAudio = null;
		Path pathVideo = null;
		Path pathAudio = null;
		
		for(int i = 0, l = mediaSingles.size(); i < l; ++i) {
			Path tempFile = tempFiles.get(i);
			Media media = mediaSingles.get(i);
			MediaType type = media.type();
			
			if(type.is(MediaType.VIDEO)) {
				pathVideo = tempFile;
				segmentsVideo = Utils.cast(((SegmentedMedia) media).segments().get(0).segments());
			} else if(type.is(MediaType.AUDIO)) {
				pathAudio = tempFile;
				segmentsAudio = Utils.cast(((SegmentedMedia) media).segments().get(0).segments());
			}
		}
		
		if(pathVideo == null || pathAudio == null) {
			throw new IllegalStateException("Both video and audio must be available");
		}
		
		if(segmentsVideo == null || segmentsAudio == null) {
			throw new IllegalStateException("Only segmentable video and audio supported");
		}
		
		DRMEngine engine = DRMEngines.fromURI(media.metadata().sourceURI());
		
		if(engine == null) {
			throw new IllegalStateException("DRM engine not found");
		}
		
		if(!checkState()) return;
		
		try {
			DecryptionProcessTracker decryptTracker = new DecryptionProcessTracker();
			manager.tracker(decryptTracker);
			
			decryptTracker.state(DecryptionProcessState.EXTRACT_PSSH);
			PSSH pssh = extractPSSH(media);
			
			if(pssh == null) {
				throw new IllegalStateException("PSSH could not be extracted");
			}
			
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
			String psshValue;
			
			if((psshValue = pssh.video()) != null) {
				MediaDecryptionRequest decryptRequest = new MediaDecryptionRequest(psshValue, request);
				List<MediaDecryptionKey> keys = WV.decryptionKeys(decryptRequest);
				keyVideo = correctDecryptionKey(fileDownloader, pathVideo, segmentsVideo, keys);
				
				if(keyVideo == null) {
					throw new IllegalStateException("Decryption key for video not found");
				}
				
				if(!checkState()) return;
			}
			
			if((psshValue = pssh.audio()) != null) {
				MediaDecryptionRequest decryptRequest = new MediaDecryptionRequest(psshValue, request);
				List<MediaDecryptionKey> keys = WV.decryptionKeys(decryptRequest);
				keyAudio = correctDecryptionKey(fileDownloader, pathAudio, segmentsAudio, keys);
				
				if(keyAudio == null) {
					throw new IllegalStateException("Decryption key for audio not found");
				}
				
				if(!checkState()) return;
			}
			
			download.start();
			
			if(!checkState()) return;
			
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
			
			// Forward the original pipeline result
			pipelineResult = (DownloadPipelineResult) result.pipelineResult();
			
			state.set(TaskStates.DONE);
		} catch(Exception ex) {
			eventRegistry.call(DownloadEvent.ERROR, new Pair<>(downloader, ex));
			throw ex; // Forward the exception
		} finally {
			stop();
		}
	}
	
	@Override
	public final void stop() throws Exception {
		if(state.is(TaskStates.STOPPED))
			return; // Nothing to do
		
		state.unset(TaskStates.RUNNING);
		state.unset(TaskStates.PAUSED);
		lockPause.unlock();
		
		if(download != null) {
			download.stop();
		}
		
		if(downloader != null) {
			downloader.stop();
		}
		
		if(!state.is(TaskStates.DONE)) {
			state.set(TaskStates.STOPPED);
		}
		
		eventRegistry.call(DownloadEvent.END, downloader);
	}
	
	@Override
	public final void pause() throws Exception {
		if(state.is(TaskStates.PAUSED))
			return; // Nothing to do
		
		if(download != null) {
			download.pause();
		}
		
		if(downloader != null) {
			downloader.pause();
		}
		
		state.unset(TaskStates.RUNNING);
		state.set(TaskStates.PAUSED);
		eventRegistry.call(DownloadEvent.PAUSE, downloader);
	}
	
	@Override
	public final void resume() throws Exception {
		if(!state.is(TaskStates.PAUSED))
			return; // Nothing to do
		
		if(download != null) {
			download.resume();
		}
		
		if(downloader != null) {
			downloader.resume();
		}
		
		state.unset(TaskStates.PAUSED);
		state.set(TaskStates.RUNNING);
		lockPause.unlock();
		eventRegistry.call(DownloadEvent.RESUME, downloader);
	}
	
	@Override
	public Download download() {
		return this;
	}
	
	@Override
	public DownloadPipelineResult pipelineResult() {
		return pipelineResult;
	}
	
	@Override
	public final boolean isRunning() {
		return state.is(TaskStates.RUNNING);
	}
	
	@Override
	public final boolean isStarted() {
		return state.is(TaskStates.STARTED);
	}
	
	@Override
	public final boolean isDone() {
		return state.is(TaskStates.DONE);
	}
	
	@Override
	public final boolean isPaused() {
		return state.is(TaskStates.PAUSED);
	}
	
	@Override
	public final boolean isStopped() {
		return state.is(TaskStates.STOPPED);
	}
	
	@Override
	public boolean isError() {
		return state.is(TaskStates.ERROR);
	}
	
	@Override
	public <V> void addEventListener(Event<? extends DownloadEvent, V> event, Listener<V> listener) {
		eventRegistry.add(event, listener);
	}
	
	@Override
	public <V> void removeEventListener(Event<? extends DownloadEvent, V> event, Listener<V> listener) {
		eventRegistry.remove(event, listener);
	}
	
	private static final class PSSH {
		
		private final String video;
		private final String audio;
		
		public PSSH(String video, String audio) {
			this.video = video;
			this.audio = audio;
		}
		
		public String video() {
			return video;
		}
		
		public String audio() {
			return audio;
		}
	}
	
	protected static class Delegator {
		
		protected final Class<?> clazz;
		
		protected Delegator(Class<?> clazz) {
			this.clazz = Objects.requireNonNull(clazz);
		}
		
		public Object callStatic(String methodName, Class<?>[] classes, Object... args) {
			try {
				// Delegate to the SegmentsDownloader to have only one point of truth
				return Reflection3.invokeStatic(clazz, methodName, classes, args);
			} catch(NoSuchMethodException
						| NoSuchFieldException
						| IllegalArgumentException
			        	| IllegalAccessException
			        	| InvocationTargetException
			        	| SecurityException ex) {
				throw new RuntimeException(ex);
			}
		}
		
		public Object call(Object instance, String methodName, Class<?>[] classes, Object... args) {
			try {
				// Delegate to the SegmentsDownloader to have only one point of truth
				return Reflection3.invoke(instance, clazz, methodName, classes, args);
			} catch(NoSuchMethodException
						| NoSuchFieldException
						| IllegalArgumentException
			        	| IllegalAccessException
			        	| InvocationTargetException
			        	| SecurityException ex) {
				throw new RuntimeException(ex);
			}
		}
	}
	
	protected static class WMSDelegator extends Delegator {
		
		private static final Class<?> clazz;
		
		static {
			try {
				clazz = Class.forName("sune.app.mediadown.downloader.wms.SegmentsDownloader");
			} catch(ClassNotFoundException ex) {
				throw new IllegalStateException(ex);
			}
		}
		
		private final Object instance;
		
		public WMSDelegator(Object instance) {
			super(clazz);
			this.instance = Objects.requireNonNull(instance);
		}
		
		public List<Media> mediaSegmentedSingles(Media media) {
			return Utils.cast(callStatic("mediaSegmentedSingles", new Class[] { Media.class }, media));
		}
		
		public List<Path> temporaryFiles(int count) {
			return Utils.cast(call(instance, "temporaryFiles", new Class[] { int.class }, count));
		}
		
		public InternalDownloader ensureInternalDownloader() {
			return Utils.cast(call(instance, "ensureInternalDownloader", new Class[0]));
		}
	}
}