package sune.app.mediadownloader.drm;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.cef.browser.CefFrame;
import org.slf4j.Logger;

import io.netty.handler.codec.http.FullHttpRequest;
import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventBindable;
import sune.app.mediadown.event.EventRegistry;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.Listener;
import sune.app.mediadown.event.PipelineEvent;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.event.tracker.WaitTracker;
import sune.app.mediadown.media.Media;
import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.util.Property;
import sune.app.mediadown.util.StateMutex;
import sune.app.mediadown.util.Threads;
import sune.app.mediadown.util.Utils.Ignore;
import sune.app.mediadownloader.drm.WidevineCDM.WidevineCDMDownloadReader;
import sune.app.mediadownloader.drm.event.DRMInstanceEvent;
import sune.app.mediadownloader.drm.event.WidevineCDMEvent;
import sune.app.mediadownloader.drm.phase.InitializationPhaseInput;
import sune.app.mediadownloader.drm.util.AudioDevices;
import sune.app.mediadownloader.drm.util.AudioDevices.AudioDevice;
import sune.app.mediadownloader.drm.util.AudioRedirector;
import sune.app.mediadownloader.drm.util.CEFLog;
import sune.app.mediadownloader.drm.util.JS;
import sune.app.mediadownloader.drm.util.Playback;
import sune.app.mediadownloader.drm.util.PlaybackData;
import sune.app.mediadownloader.drm.util.PlaybackEventsHandler;
import sune.app.mediadownloader.drm.util.ProcessManager;
import sune.util.ssdf2.SSDCollection;

public final class DRMInstance implements EventBindable<EventType> {
	
	private static final Logger logger = DRMLog.get();
	private static final int DEFAULT_BROWSER_WIDTH = 800;
	private static final int DEFAULT_BROWSER_HEIGHT = 600;
	
	private final DRMInstanceMode mode;
	private final DRMEngine engine;
	private final String url;
	private final UUID uuid = UUID.randomUUID();
	
	private final Pipeline pipeline = Pipeline.create();
	
	private final DRMContext context;
	private DRMBrowserContext browserContext;
	
	private final AtomicReference<Exception> exception = new AtomicReference<>(null);
	private final StateMutex mtxDone = new StateMutex();
	private final StateMutex mtxReady = new StateMutex();
	private final StateMutex mtxInit = new StateMutex();
	
	private volatile PlaybackEventsHandler playbackEventsHandler;
	private volatile boolean playbackStarted;
	private volatile boolean playbackEnded;
	private final AtomicBoolean playbackReady = new AtomicBoolean();
	private volatile Playback playback;
	private volatile ProcessManager processManager;
	private volatile AudioDevice audioDevice;
	private volatile DRMCommandFactory commandFactory;
	
	private final EventRegistry<EventType> eventRegistry = new EventRegistry<>();
	private final TrackerManager manager = new TrackerManager();
	
	private final DRMConfiguration configuration;
	
	private final AtomicBoolean running = new AtomicBoolean();
	private final AtomicBoolean started = new AtomicBoolean();
	private final AtomicBoolean done = new AtomicBoolean();
	private final AtomicBoolean paused = new AtomicBoolean();
	private final AtomicBoolean stopped = new AtomicBoolean();
	
	private static final List<DRMInstance> instances = new ArrayList<>();
	
	private DRMInstance(DRMInstanceMode mode, DRMEngine engine, String url, DRMConfiguration configuration) {
		this.mode = Objects.requireNonNull(mode);
		this.engine = engine;
		this.url = url;
		this.configuration = configuration;
		this.context = new Context();
		manager.tracker(new WaitTracker());
		instances.add(this);
	}
	
	private DRMInstance(DRMInstanceMode mode) {
		this(mode, null, null, null);
	}
	
	public static final DRMInstance withEngine(DRMEngine engine, String url, DRMConfiguration configuration)
			throws Exception {
		return new DRMInstance(DRMInstanceMode.DRM_ENGINE, engine, url, configuration);
	}
	
	public static final DRMInstance downloadWidevineDRM() throws Exception {
		return new DRMInstance(DRMInstanceMode.WIDEVINE_CDM_DOWNLOAD);
	}
	
	public static final List<DRMInstance> instances() {
		return List.copyOf(instances);
	}
	
	private static final AudioDevice tryGetAudioDevice() throws Exception {
		AudioDevice audioDevice;
		
		if(logger.isDebugEnabled())
			logger.debug("Trying to find a virtual audio device...");
		
		// If no virtual device is available, try to get the Stereo mix audio device
		if((audioDevice = AudioDevices.virtualDevice()) == null) {
			if(logger.isDebugEnabled())
				logger.debug("Virtual audio device not found. Trying to find Stereo mix device...");
			
			// Fail if no audio device matches
			if((audioDevice = AudioDevices.stereoMixDevice()) == null)
				throw new IllegalStateException("Unable to obtain Stereo mix audio device.");
		}
		
		// At this point the device is either the virtual one or the Stereo mix one
		return audioDevice;
	}
	
	private final void prepareAudioDevice() throws Exception {
		audioDevice = tryGetAudioDevice();
		if(logger.isDebugEnabled())
			logger.debug("Audio device name: {} (isVirtual={})",
			             audioDevice.alternativeName(), audioDevice.isVirtual());
		
		// Check if the selected audio device is the virtual one
		if(audioDevice.isVirtual()) {
			if(logger.isDebugEnabled())
				logger.debug("Redirecting to the virtual audio device...");
			AudioRedirector.redirectToVirtual();
			if(logger.isDebugEnabled())
				logger.debug("Audio redirected to the virtual audio device.");
		}
	}
	
	private final void doProcess(Path output, double duration, int videoID, long frameID) throws Exception {
		prepareAudioDevice();
		
		DRMBrowser browser = browserContext.browser();
		CefFrame frame = browser.cefBrowser().getFrame(frameID);
		
		playbackStarted = false;
		playbackEnded = false;
		playbackReady.set(false);
		playback = new Playback(browser, frame, videoID);
		JS.Playback.include(frame);
		JS.Helper.enableDoUserInteraction(browser, frame);
		
		processManager = new ProcessManager();
		commandFactory = new DRMCommandFactory(configuration);
		pipeline.setInput(new InitializationPhaseInput(context, duration));
		mtxInit.unlock();
		mtxReady.await();
		pipeline.start();
	}
	
	private final void error(Exception ex) {
		exception.set(ex);
		mtxDone.unlock();
	}
	
	private final void success() {
		mtxDone.unlock();
	}
	
	private final void doMode_DRMEngine() throws Exception {
		if(logger.isDebugEnabled()) {
			logger.debug("Current mode: DRM Engine");
			logger.debug("Configuration: quality={}", configuration.quality());
		}
		
		int width = DEFAULT_BROWSER_WIDTH, height = DEFAULT_BROWSER_HEIGHT;
		Path output = configuration.output();
		Media media = configuration.media();
		DRMResolver resolver = engine.createResolver(context, url, output, media);
		DRMBrowser browser = DRM.createClient(context, resolver).createBrowser(width, height);
		String initUrl = resolver.url(); // Get before starting the browser
		
		browser.start();
		browser.client().awaitLoaded();
		browser.load(initUrl);
		browserContext = browser.context();
		
		pipeline.addEventListener(PipelineEvent.ERROR, (pair) -> error(pair.b));
		pipeline.addEventListener(PipelineEvent.END, (p) -> success());
	}
	
	private final void doMode_WidevineCDMDownload() throws Exception {
		if(logger.isDebugEnabled())
			logger.debug("Current mode: Widevine CDM download");
		
		eventRegistry.call(WidevineCDMEvent.BEGIN, context);
		
		// Window width must be > 0, otherwise the request process fails.
		int width = 1, height = 1;
		DRM.setCefLogEnabled(true);
		DRMBrowser browser = DRM.createClient(context, new DummyDRMResolver()).createBrowser(width, height);
		
		if(CEFLog.instance() == null)
			throw new IllegalStateException("CefLog is not initialized.");
		
		CEFLog cefLog = CEFLog.instance();
		cefLog.start();
		// Inject log reader for manual Widevine CDM download
		WidevineCDMDownloadReader reader = new WidevineCDMDownloadReader(context);
		cefLog.registerReader(reader);
		browser.start();
		
		if(logger.isDebugEnabled())
			logger.debug("Waiting for Widevine CDM download request made by CEF (up to 10 seconds)...");
		
		eventRegistry.call(WidevineCDMEvent.WAIT_CEF_REQUEST, context);
		
		try {
			// Wait for CEF to make a Widevine CDM download request
			cefLog.await();
		} catch(IOException ex) {
			if(logger.isDebugEnabled())
				logger.debug("CefLog await failed.");
		}
		
		browser.client().awaitLoaded();
		browser.context().close();
		
		reader.awaitDownloaded();
		
		eventRegistry.call(WidevineCDMEvent.END, context);
		
		mtxDone.unlock();
	}
	
	private final void start() throws Exception {
		switch(mode) {
			case DRM_ENGINE: doMode_DRMEngine(); break;
			case WIDEVINE_CDM_DOWNLOAD: doMode_WidevineCDMDownload(); break;
			default:
				throw new IllegalStateException("Invalid DRM Instance mode: " + mode);
		}
	}
	
	private final void await() throws Exception {
		mtxDone.await();
		Exception ex = exception.getAndSet(null);
		if(ex != null) throw ex;
	}
	
	public final void startAndWait() throws Exception {
		running.set(true);
		started.set(true);
		eventRegistry.call(DRMInstanceEvent.BEGIN, context);
		try {
			start();
			await();
		} finally {
			running.set(false);
			if(!stopped.get()) {
				done.set(true);
				eventRegistry.call(DRMInstanceEvent.END, context);
			}
			instances.remove(this);
		}
	}
	
	public void stop() throws Exception {
		if(!running.get()) return; // Do not continue
		running.set(false);
		context.browserContext().close();
		pipeline.stop();
		if(!done.get()) stopped.set(true);
		eventRegistry.call(DRMInstanceEvent.END, context);
	}
	
	public void pause() throws Exception {
		if(!running.get()) return; // Do not continue
		pipeline.pause();
		running.set(false);
		paused .set(true);
		eventRegistry.call(DRMInstanceEvent.PAUSE, context);
	}
	
	public void resume() throws Exception {
		if(running.get()) return; // Do not continue
		pipeline.resume();
		paused .set(false);
		running.set(true);
		eventRegistry.call(DRMInstanceEvent.RESUME, context);
	}
	
	public final boolean isRunning() {
		return running.get();
	}
	
	public final boolean isStarted() {
		return started.get();
	}
	
	public final boolean isDone() {
		return done.get();
	}
	
	public final boolean isPaused() {
		return paused.get();
	}
	
	public final boolean isStopped() {
		return stopped.get();
	}
	
	private final void syncTime(PlaybackData data) {
		if(playbackEventsHandler != null)
			playbackEventsHandler.updated(data);
	}
	
	private final void syncWait(PlaybackData data) {
		if(playbackEventsHandler != null)
			playbackEventsHandler.waiting(data);
	}
	
	private final void syncResume(PlaybackData data) {
		if(playbackEventsHandler != null)
			playbackEventsHandler.resumed(data);
	}
	
	private final void syncStop(PlaybackData data) {
		if(playbackEventsHandler != null)
			playbackEventsHandler.ended(data);
		
		playbackEnded = true;
	}
	
	private final void playbackPause(PlaybackData data) {
		if(!playbackStarted) return;
		
		if(logger.isDebugEnabled())
			logger.debug("Video playback paused");
		
		playback.pause();
	}
	
	private final void playbackResume(PlaybackData data) {
		if(!playbackStarted) return;
		
		if(logger.isDebugEnabled())
			logger.debug("Video playback resumed");
		
		playback.play();
	}
	
	private final void playbackReady(PlaybackData data) {
		// Playback already ended, nothing to do
		if(playbackEnded) return;
		
		if(logger.isDebugEnabled())
			logger.debug("Playback ready {}", data.time);
		
		// Video can be played (first time)
		if(!playbackStarted) {
			// Allow only one call to this procedure
			if(!playbackReady.compareAndSet(false, true))
				return;
			
			(Threads.newThreadUnmanaged(() -> {
				Property<Double> time = new Property<>(data.time);
				
				if(logger.isDebugEnabled())
					logger.debug("Waiting for fields initialization...");
				
				// Ensure that the playback field is set
				mtxInit.await();
				
				if(logger.isDebugEnabled())
					logger.debug("Fields initialization completed.");
				
				if(logger.isDebugEnabled())
					logger.debug("Checking if the video is playing...");
				
				boolean isPlaying = Ignore.defaultValue(playback.isPlaying()::get, false);
				
				if(logger.isDebugEnabled())
					logger.debug("Video is playing: {}.", isPlaying);
				
				if(isPlaying) {
					if(logger.isDebugEnabled())
						logger.debug("Video is playing. Pausing...");
					
					Ignore.callVoid(playback.pause()::await);
				}
				
				// Time must be set to 0.0 seconds manually
				if(time.getValue() != 0.0) {
					if(logger.isDebugEnabled())
						logger.debug("Time is not 0.0 seconds. Setting time to 0.0 seconds...");
					
					// Set the time to 0.0 seconds
					Ignore.callVoid(playback.time(0.0, true).then(() -> time.setValue(0.0))::await);
					
					if(logger.isDebugEnabled())
						logger.debug("Time set to 0.0 seconds.");
				}
				
				if(time.getValue() == 0.0) {
					if(logger.isDebugEnabled())
						logger.debug("Time is 0.0 seconds. All is ready.");
					
					playbackStarted = true;
					mtxReady.unlock();
				}
			})).start();
		}
	}
	
	@Override
	public <V> void addEventListener(Event<? extends EventType, V> event, Listener<V> listener) {
		eventRegistry.add(event, listener);
	}
	
	@Override
	public <V> void removeEventListener(Event<? extends EventType, V> event, Listener<V> listener) {
		eventRegistry.remove(event, listener);
	}
	
	public final EventRegistry<EventType> eventRegistry() {
		return eventRegistry;
	}
	
	public DRMContext context() {
		return context;
	}
	
	private static enum DRMInstanceMode {
		
		DRM_ENGINE, WIDEVINE_CDM_DOWNLOAD;
	}
	
	private static final class DummyDRMResolver implements DRMResolver {
		
		@Override public void onLoadStart(DRMBrowser browser, CefFrame frame) {}
		@Override public void onLoadEnd(DRMBrowser browser, CefFrame frame, int httpStatusCode) {}
		@Override public void onRequest(DRMBrowser browser, CefFrame frame, String requestName, SSDCollection json,
				String request) {}
		@Override public boolean shouldModifyResponse(String uri, String mimeType, Charset charset,
				FullHttpRequest request) { return false; }
		@Override public String modifyResponse(String uri, String mimeType, Charset charset, String content,
				FullHttpRequest request) { return content; }
		@Override public String url() { return null; }
	}
	
	private final class Context implements DRMContext {
		
		private final AtomicBoolean isReady = new AtomicBoolean();
		
		@Override
		public void ready(double duration, int videoID, long frameID) {
			// Allow only one call to this method
			if(!isReady.compareAndSet(false, true))
				return;
			
			Path output = configuration.output();
			if(logger.isDebugEnabled())
				logger.debug("Record request: output={}, duration={}, videoID={}, frameID={}",
				             output.toAbsolutePath().toString(), duration, videoID, frameID);
			
			try {
				DRMInstance.this.doProcess(output, duration, videoID, frameID);
			} catch(Exception ex) {
				DRMInstance.this.error(ex);
			}
		}
		
		@Override
		public boolean hasRecordStarted() {
			return pipeline.isStarted();
		}
		
		@Override
		public void playbackEventsHandler(PlaybackEventsHandler handler) {
			playbackEventsHandler = handler;
		}
		
		@Override
		public void syncTime(PlaybackData data) {
			DRMInstance.this.syncTime(data);
		}
		
		@Override
		public void syncWait(PlaybackData data) {
			DRMInstance.this.syncWait(data);
		}
		
		@Override
		public void syncResume(PlaybackData data) {
			DRMInstance.this.syncResume(data);
		}
		
		@Override
		public void syncStop(PlaybackData data) {
			DRMInstance.this.syncStop(data);
		}
		
		@Override
		public void playbackPause(PlaybackData data) {
			DRMInstance.this.playbackPause(data);
		}
		
		@Override
		public void playbackResume(PlaybackData data) {
			DRMInstance.this.playbackResume(data);
		}
		
		@Override
		public void playbackReady(PlaybackData data) {
			DRMInstance.this.playbackReady(data);
		}
		
		@Override
		public EventRegistry<EventType> eventRegistry() {
			return eventRegistry;
		}
		
		@Override
		public TrackerManager trackerManager() {
			return manager;
		}
		
		@Override
		public DRMBrowserContext browserContext() {
			return browserContext;
		}
		
		@Override
		public Playback playback() {
			return playback;
		}
		
		@Override
		public ProcessManager processManager() {
			return processManager;
		}
		
		@Override
		public DRMCommandFactory commandFactory() {
			return commandFactory;
		}
		
		@Override
		public UUID uuid() {
			return uuid;
		}
		
		@Override
		public DRMConfiguration configuration() {
			return configuration;
		}
		
		@Override
		public String audioDeviceName() {
			return audioDevice.alternativeName();
		}
	}
}