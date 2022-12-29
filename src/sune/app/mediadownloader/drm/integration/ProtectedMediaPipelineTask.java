package sune.app.mediadownloader.drm.integration;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import sune.api.process.Processes;
import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventRegistry;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.tracker.SimpleTracker;
import sune.app.mediadown.event.tracker.Tracker;
import sune.app.mediadown.event.tracker.TrackerEvent;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.language.Translation;
import sune.app.mediadown.media.Media;
import sune.app.mediadown.pipeline.DownloadPipelineResult;
import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadown.pipeline.PipelineTaskRegistry.PipelineTaskInputData;
import sune.app.mediadown.util.MathUtils;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.StateMutex;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRM;
import sune.app.mediadownloader.drm.DRMBootstrap;
import sune.app.mediadownloader.drm.DRMBootstrapCLI;
import sune.app.mediadownloader.drm.DRMConfiguration;
import sune.app.mediadownloader.drm.DRMContext;
import sune.app.mediadownloader.drm.DRMEngine;
import sune.app.mediadownloader.drm.DRMEngines;
import sune.app.mediadownloader.drm.DRMInstance;
import sune.app.mediadownloader.drm.WidevineCDM;
import sune.app.mediadownloader.drm.event.AnalyzeEvent;
import sune.app.mediadownloader.drm.event.DRMInstanceEvent;
import sune.app.mediadownloader.drm.event.PostProcessEvent;
import sune.app.mediadownloader.drm.event.RecordEvent;
import sune.app.mediadownloader.drm.event.WidevineCDMEvent;

public class ProtectedMediaPipelineTask implements PipelineTask<DownloadPipelineResult> {
	
	private static final int MAX_WORKERS = 1;
	
	private static final StateMutex mutex = new StateMutex();
	private static int count = 0;
	
	private final Media media;
	private final Path destination;
	private DRMInstance instance;
	
	private Pipeline pipeline;
	private TextProgressSimpleTracker tracker;
	
	private ProtectedMediaPipelineTask(PipelineTaskInputData data) {
		if(data == null)
			throw new IllegalArgumentException();
		this.media = Objects.requireNonNull(data.get("media"));
		this.destination = Objects.requireNonNull(data.get("destination"));
	}
	
	private static final void lock() throws Exception {
		synchronized(mutex) {
			for(Throwable ex; count >= MAX_WORKERS;) {
				mutex.awaitAndReset();
				if((ex = mutex.getExceptionAndReset()) != null)
					throw (Exception) ex;
			}
			++count;
		}
	}
	
	private static final void unlock() {
		synchronized(mutex) {
			--count;
			mutex.unlock();
		}
	}
	
	private final void eventRegistryUpdate(Tracker tracker) {
		pipeline.getEventRegistry().call(TrackerEvent.UPDATE, tracker);
	}
	
	private final void eventRegistryError(Tracker tracker, Exception exception) {
		pipeline.getEventRegistry().call(TrackerEvent.ERROR, new Pair<>(tracker, exception));
	}
	
	private final void addDefaultBootstrapListeners(EventRegistry<EventType> eventRegistry, Translation translation) {
		eventRegistry.add(WidevineCDMEvent.BEGIN, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.begin"));
			eventRegistryUpdate(tracker);
		});
		eventRegistry.add(WidevineCDMEvent.WAIT_CEF_REQUEST, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.wait_cef_request"));
			eventRegistryUpdate(tracker);
		});
		eventRegistry.add(WidevineCDMEvent.END, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.end"));
			eventRegistryUpdate(tracker);
		});
		eventRegistry.add(WidevineCDMEvent.ERROR, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.error"));
			eventRegistryError(tracker, o.b);
		});
		eventRegistry.add(WidevineCDMEvent.BEGIN_REQUEST, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.begin_request"));
			eventRegistryUpdate(tracker);
		});
		eventRegistry.add(WidevineCDMEvent.END_REQUEST, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.end_request"));
			eventRegistryUpdate(tracker);
		});
		eventRegistry.add(WidevineCDMEvent.BEGIN_DOWNLOAD, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.begin_download"));
			eventRegistryUpdate(tracker);
		});
		eventRegistry.add(WidevineCDMEvent.UPDATE_DOWNLOAD, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.update_download",
				"percent", MathUtils.round(o.b.tracker().progress() * 100.0, 2)));
			eventRegistryUpdate(tracker);
		});
		eventRegistry.add(WidevineCDMEvent.END_DOWNLOAD, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.end_download"));
			eventRegistryUpdate(tracker);
		});
		eventRegistry.add(WidevineCDMEvent.BEGIN_EXTRACT, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.begin_extract"));
			eventRegistryUpdate(tracker);
		});
		eventRegistry.add(WidevineCDMEvent.END_EXTRACT, (o) -> {
			tracker.state(DRMProgressStates.WIDEVINE);
			tracker.text(translation.getSingle("widevine.end_extract"));
			eventRegistryUpdate(tracker);
		});
	}
	
	private final void addDefaultListeners(DRMInstance instance, Pipeline pipeline, Translation translation) {
		instance.addEventListener(DRMInstanceEvent.BEGIN, (o) -> {
			tracker.state(DRMProgressStates.INSTANCE);
			tracker.text(translation.getSingle("drm_instance.begin"));
			eventRegistryUpdate(tracker);
		});
		instance.addEventListener(DRMInstanceEvent.END, (o) -> {
			tracker.state(DRMProgressStates.INSTANCE);
			tracker.text(translation.getSingle("drm_instance.end"));
			eventRegistryUpdate(tracker);
		});
		instance.addEventListener(DRMInstanceEvent.ERROR, (o) -> {
			tracker.state(DRMProgressStates.INSTANCE);
			tracker.text(translation.getSingle("drm_instance.error"));
			eventRegistryError(tracker, o.b);
		});
		
		instance.addEventListener(AnalyzeEvent.BEGIN, (o) -> {
			tracker.state(DRMProgressStates.ANALYZE);
			tracker.text(translation.getSingle("phase.analyze.begin"));
			eventRegistryUpdate(tracker);
		});
		instance.addEventListener(AnalyzeEvent.UPDATE, (o) -> {
			eventRegistryUpdate(o.b.tracker());
		});
		instance.addEventListener(AnalyzeEvent.END, (o) -> {
			tracker.state(DRMProgressStates.ANALYZE);
			tracker.text(translation.getSingle("phase.analyze.end"));
			eventRegistryUpdate(tracker);
		});
		instance.addEventListener(AnalyzeEvent.ERROR, (o) -> {
			tracker.state(DRMProgressStates.ANALYZE);
			tracker.text(translation.getSingle("phase.analyze.error"));
			eventRegistryError(tracker, o.b);
		});
		
		instance.addEventListener(RecordEvent.BEGIN, (o) -> {
			tracker.state(DRMProgressStates.RECORD);
			tracker.text(translation.getSingle("phase.record.begin"));
			eventRegistryUpdate(tracker);
		});
		instance.addEventListener(RecordEvent.UPDATE, (o) -> {
			eventRegistryUpdate(o.b.tracker());
		});
		instance.addEventListener(RecordEvent.END, (o) -> {
			tracker.state(DRMProgressStates.RECORD);
			tracker.text(translation.getSingle("phase.record.end"));
			eventRegistryUpdate(tracker);
		});
		instance.addEventListener(RecordEvent.ERROR, (o) -> {
			tracker.state(DRMProgressStates.RECORD);
			tracker.text(translation.getSingle("phase.record.error"));
			eventRegistryError(tracker, o.b);
		});
		
		instance.addEventListener(PostProcessEvent.BEGIN, (o) -> {
			tracker.state(DRMProgressStates.POST_PROCESS);
			tracker.text(translation.getSingle("phase.post_process.begin"));
			eventRegistryUpdate(tracker);
		});
		instance.addEventListener(PostProcessEvent.UPDATE, (o) -> {
			eventRegistryUpdate(o.b.tracker());
		});
		instance.addEventListener(PostProcessEvent.END, (o) -> {
			tracker.state(DRMProgressStates.POST_PROCESS);
			tracker.text(translation.getSingle("phase.post_process.end"));
			eventRegistryUpdate(tracker);
		});
		instance.addEventListener(PostProcessEvent.ERROR, (o) -> {
			tracker.state(DRMProgressStates.POST_PROCESS);
			tracker.text(translation.getSingle("phase.post_process.error"));
			eventRegistryError(tracker, o.b);
		});
	}
	
	private final void runDownloadWidevineCDMProcessAndWait(EventRegistry<EventType> dummyEventRegistry) throws Exception {
		List<String> args = new ArrayList<>();
		if(!IntegrationUtils.runInJAR()) {
			args.add("-Dfile.encoding=UTF-8");
			args.add("-p");
			args.add("\"" + System.getProperties().get("jdk.module.path") + "\"");
			args.add("--add-modules");
			args.add("ALL-SYSTEM");
			args.add("-m");
			args.add("sune.app.mediadown/sune.app.mediadown.App");
		}
		args.add("-jar");
		args.add("\"" + IntegrationUtils.jarPath().toString() + "\"");
		args.add("--no-startup-gui");
		args.add("--plugin drm");
		args.add("--class sune.app.mediadownloader.drm.DRMBootstrapCLI");
		args.add("--download-widevine-cdm");
		String command = args.stream().reduce("", (a, b) -> a + " " + b).stripLeading();
		Path exePath = Path.of(ProcessHandle.current().info().command().orElseThrow()).toAbsolutePath();
		Consumer<String> parser = new DownloadWidevineCDMProcessLineParser(dummyEventRegistry);
		try(ReadOnlyProcess process = Processes.createAsynchronous(exePath, parser)) {
			process.execute(command, IntegrationUtils.currentDirectory());
			// Wait for the process to finish
			if(process.waitFor() != 0) {
				throw new IllegalStateException("Unable to download Widevine CDM.");
			}
		}
	}
	
	@Override
	public final DownloadPipelineResult run(Pipeline pipeline) throws Exception {
		try {
			lock(); // Wait for available worker space, if needed
			// Get the original video URL that was used for the media and check it
			String url = Optional.ofNullable(media.metadata().sourceURI()).map(URI::toString).orElse(null);
			if(url == null || !Utils.isValidURL(url))
				throw new IllegalArgumentException("Invalid url");
			// Obtain the DRM engine to be used for the URL
			DRMEngine engine = DRMEngines.fromURL(url);
			if(engine == null)
				throw new IllegalStateException("No DRM engine found");
			// Prepare events-related variables
			this.pipeline = pipeline;
			this.tracker = new TextProgressSimpleTracker();
			// Obtain plugins translation
			Translation translation = IntegrationUtils.translation();
			// Ensure there is Widevine CDM installed and ready
			if(!WidevineCDM.isInstalled()) {
				// Run the download process in a new separate process since we can initialize
				// the JCEF/CEF system only once and that must be with Widevine CDM ready.
				EventRegistry<EventType> dummyEventRegistry = new EventRegistry<>();
				addDefaultBootstrapListeners(dummyEventRegistry, translation);
				runDownloadWidevineCDMProcessAndWait(dummyEventRegistry);
			}
			// Create the output file so that the "Show file" function works
			NIO.createFile(destination);
			
			// Obtain quality from the configuration
			DRMPluginConfiguration pluginConfiguration = DRMPluginConfiguration.instance();
			DRMConfiguration.Quality quality = pluginConfiguration.quality();
			
			// Configure the DRM
			DRMConfiguration configuration = new DRMConfiguration.Builder()
					.output(destination)
					.media(media)
					.detectFPS(false) // Do not automatically detect FPS for now
					.quality(quality)
					.build();
			
			// Prepare the DRM instance
			instance = DRMInstance.withEngine(engine, url, configuration);
			addDefaultListeners(instance, pipeline, translation);
			// Finally, run the instance and obtain the file
			instance.startAndWait();
			// No conversion is needed afterwards
			return DownloadPipelineResult.noConversion();
		} finally {
			// Delete the Cef debug file, if empty and in non-debug mode
			if(!DRMBootstrap.instance().debug()) {
				try {
					Path path = DRM.cefLogFile();
					long size = Utils.ignore(() -> NIO.size(path), 0L);
					if(size <= 0) NIO.deleteFile(path);
				} catch(Exception ex) {
					// Ignore
				}
			}
			unlock(); // Free the worker space
		}
	}
	
	@Override
	public final void stop() throws Exception {
		if(instance != null) {
			instance.stop();
		}
	}
	
	@Override
	public final void pause() throws Exception {
		if(instance != null) {
			instance.pause();
		}
	}
	
	@Override
	public final void resume() throws Exception {
		if(instance != null) {
			instance.resume();
		}
	}
	
	@Override
	public final boolean isRunning() {
		return instance != null && instance.isRunning();
	}
	
	@Override
	public final boolean isStarted() {
		return instance != null && instance.isStarted();
	}
	
	@Override
	public final boolean isDone() {
		return instance != null && instance.isDone();
	}
	
	@Override
	public final boolean isPaused() {
		return instance != null && instance.isPaused();
	}
	
	@Override
	public final boolean isStopped() {
		return instance != null && instance.isStopped();
	}
	
	private final class TextProgressSimpleTracker extends SimpleTracker {
		
		private String state;
		private String text;
		
		public void state(String state) {
			this.state = state;
			update();
		}
		
		public void text(String text) {
			this.text = text;
			update();
		}
		
		@Override
		public double progress() {
			return Double.NaN;
		}
		
		@Override
		public String textProgress() {
			return text;
		}
		
		@Override
		public String state() {
			return state;
		}
	}
	
	private static final class DownloadWidevineCDMProcessLineParser implements Consumer<String> {
		
		private static Map<String, Event<?, ?>> eventTypes;
		
		private final String prefix = DRMBootstrapCLI.linePrefix();
		private final EventRegistry<EventType> eventRegistry;
		
		private boolean isAccumulating;
		private StringBuilder accumulator;
		
		private TrackerManager trackerManager;
		private DummyDownloadTracker downloadTracker;
		
		private DownloadWidevineCDMProcessLineParser(EventRegistry<EventType> eventRegistry) {
			this.eventRegistry = eventRegistry;
		}
		
		@SuppressWarnings("unchecked")
		private static final <T extends EventType, P> Event<T, P> nameToEventType(Class<T> clazz, String name) {
			if(eventTypes == null) {
				eventTypes = new HashMap<>();
				for(Field field : clazz.getDeclaredFields()) {
					try {
						eventTypes.put(field.getName(), (Event<?, ?>) field.get(null));
					} catch(Exception ex) {
						// Ignore
					}
				}
			}
			return (Event<T, P>) eventTypes.get(name);
		}
		
		@Override
		public void accept(String line) {
			if(!line.startsWith(prefix))
				return; // Ignore lines without the prefix
			line = line.substring(prefix.length() + 1);
			if(line.isEmpty() && isAccumulating) {
				String exceptionText = accumulator.toString();
				isAccumulating = false;
				eventRegistry.call(WidevineCDMEvent.ERROR, new Pair<>((DRMContext) null, new Exception(exceptionText)));
				return; // Do not continue
			}
			int index = line.indexOf(' ');
			if(index >= 0) {
				// Additional arguments present
				String name = line.substring(0, index);
				Event<WidevineCDMEvent, ?> eventType = nameToEventType(WidevineCDMEvent.class, name);
				if(eventType == WidevineCDMEvent.ERROR) {
					isAccumulating = true;
					if(accumulator == null) {
						accumulator = new StringBuilder();
					}
					accumulator.setLength(0);
				} else if(eventType == WidevineCDMEvent.UPDATE_DOWNLOAD) {
					String[] args = line.substring(index + 1).split(" ");
					double percent = Double.valueOf(args[0]);
					if(downloadTracker == null) {
						trackerManager = new TrackerManager();
						downloadTracker = new DummyDownloadTracker();
						trackerManager.tracker(downloadTracker);
					}
					downloadTracker.setProgress(percent);
					eventRegistry.call(WidevineCDMEvent.UPDATE_DOWNLOAD, new Pair<>(null, trackerManager));
				}
			} else {
				// No additional arguments, just the name
				eventRegistry.call(nameToEventType(WidevineCDMEvent.class, line));
			}
		}
		
		private static final class DummyDownloadTracker extends SimpleTracker {
			
			private double progress = 0.0;
			
			public void setProgress(double progress) {
				this.progress = progress;
			}
			
			@Override
			public double progress() {
				return progress;
			}
		}
	}
}