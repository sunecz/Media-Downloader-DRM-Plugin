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
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.IEventType;
import sune.app.mediadown.event.Listener;
import sune.app.mediadown.event.PipelineEvent;
import sune.app.mediadown.event.tracker.SimpleTracker;
import sune.app.mediadown.event.tracker.Tracker;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.language.Translation;
import sune.app.mediadown.media.Media;
import sune.app.mediadown.pipeline.DownloadPipelineResult;
import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.Pipeline.PipelineEventRegistry;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadown.pipeline.PipelineTaskRegistry.PipelineTaskInputData;
import sune.app.mediadown.util.MathUtils;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRM;
import sune.app.mediadownloader.drm.DRMBootstrap;
import sune.app.mediadownloader.drm.DRMBootstrapCLI;
import sune.app.mediadownloader.drm.DRMConfiguration;
import sune.app.mediadownloader.drm.DRMContext;
import sune.app.mediadownloader.drm.DRMEngine;
import sune.app.mediadownloader.drm.DRMEngines;
import sune.app.mediadownloader.drm.DRMEventRegistry;
import sune.app.mediadownloader.drm.DRMInstance;
import sune.app.mediadownloader.drm.WidevineCDM;
import sune.app.mediadownloader.drm.event.AnalyzeEvent;
import sune.app.mediadownloader.drm.event.DRMInstanceEvent;
import sune.app.mediadownloader.drm.event.PostProcessEvent;
import sune.app.mediadownloader.drm.event.RecordEvent;
import sune.app.mediadownloader.drm.event.WidevineCDMEvent;
import sune.app.mediadownloader.drm.tracker.AnalyzeTracker;
import sune.app.mediadownloader.drm.tracker.EnumNameTracker;
import sune.app.mediadownloader.drm.tracker.PostProcessTracker;
import sune.app.mediadownloader.drm.tracker.RecordTracker;
import sune.app.mediadownloader.drm.util.StateMutex;

public class ProtectedMediaPipelineTask implements PipelineTask<DownloadPipelineResult> {
	
	private static final int MAX_WORKERS = 1;
	
	private static final StateMutex mutex = new StateMutex();
	private static int count = 0;
	
	private final Media media;
	private final Path destination;
	private DRMInstance instance;
	
	private TrackerManager manager;
	private TextProgressSimpleTracker tracker;
	private Listener<Object> listenerUpdate;
	private Listener<Object> listenerError;
	
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
	
	private final void eventRegistryUpdate(Pipeline pipeline) {
		if(listenerUpdate != null)
			listenerUpdate.call(new Pair<>((Object) null, manager));
	}
	
	private final void eventRegistryError(Pipeline pipeline, Exception exception) {
		if(listenerError != null)
			listenerError.call(new Pair<>(pipeline, exception));
	}
	
	private final void addDefaultBootstrapListeners(DRMEventRegistry eventRegistry, Pipeline pipeline, Translation translation) {
		eventRegistry.add(WidevineCDMEvent.BEGIN, (o) -> {
			tracker.setText(translation.getSingle("widevine.begin"));
			eventRegistryUpdate(pipeline);
		});
		eventRegistry.add(WidevineCDMEvent.WAIT_CEF_REQUEST, (o) -> {
			tracker.setText(translation.getSingle("widevine.wait_cef_request"));
			eventRegistryUpdate(pipeline);
		});
		eventRegistry.add(WidevineCDMEvent.END, (o) -> {
			tracker.setText(translation.getSingle("widevine.end"));
			eventRegistryUpdate(pipeline);
		});
		eventRegistry.add(WidevineCDMEvent.ERROR, (o) -> {
			tracker.setText(translation.getSingle("widevine.error"));
			eventRegistryError(pipeline, o.b);
		});
		eventRegistry.add(WidevineCDMEvent.BEGIN_REQUEST, (o) -> {
			tracker.setText(translation.getSingle("widevine.begin_request"));
			eventRegistryUpdate(pipeline);
		});
		eventRegistry.add(WidevineCDMEvent.END_REQUEST, (o) -> {
			tracker.setText(translation.getSingle("widevine.end_request"));
			eventRegistryUpdate(pipeline);
		});
		eventRegistry.add(WidevineCDMEvent.BEGIN_DOWNLOAD, (o) -> {
			tracker.setText(translation.getSingle("widevine.begin_download"));
			eventRegistryUpdate(pipeline);
		});
		eventRegistry.add(WidevineCDMEvent.UPDATE_DOWNLOAD, (o) -> {
			tracker.setText(translation.getSingle("widevine.update_download",
				"percent", MathUtils.round(o.b.getTracker().getProgress() * 100.0, 2)));
			eventRegistryUpdate(pipeline);
		});
		eventRegistry.add(WidevineCDMEvent.END_DOWNLOAD, (o) -> {
			tracker.setText(translation.getSingle("widevine.end_download"));
			eventRegistryUpdate(pipeline);
		});
		eventRegistry.add(WidevineCDMEvent.BEGIN_EXTRACT, (o) -> {
			tracker.setText(translation.getSingle("widevine.begin_extract"));
			eventRegistryUpdate(pipeline);
		});
		eventRegistry.add(WidevineCDMEvent.END_EXTRACT, (o) -> {
			tracker.setText(translation.getSingle("widevine.end_extract"));
			eventRegistryUpdate(pipeline);
		});
	}
	
	private final void addDefaultListeners(DRMInstance instance, Pipeline pipeline, Translation translation) {
		instance.addEventListener(DRMInstanceEvent.BEGIN, (o) -> {
			tracker.setText(translation.getSingle("drm_instance.begin"));
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(DRMInstanceEvent.END, (o) -> {
			tracker.setText(translation.getSingle("drm_instance.end"));
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(DRMInstanceEvent.ERROR, (o) -> {
			tracker.setText(translation.getSingle("drm_instance.error"));
			eventRegistryError(pipeline, o.b);
		});

		instance.addEventListener(AnalyzeEvent.BEGIN, (o) -> {
			tracker.setText(translation.getSingle("phase.analyze.begin"));
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(AnalyzeEvent.UPDATE, (o) -> {
			AnalyzeTracker phaseTracker = (AnalyzeTracker) o.b.getTracker();
			String progress = translation.getSingle("phase.analyze.update",
				"percent",      MathUtils.round(phaseTracker.getProgress() * 100.0, 2),
				"current_time", MathUtils.round(phaseTracker.getCurrentTime(), 2),
				"total_time",   MathUtils.round(phaseTracker.getTotalTime(), 2));
			tracker.setText(progress);
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(AnalyzeEvent.END, (o) -> {
			tracker.setText(translation.getSingle("phase.analyze.end"));
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(AnalyzeEvent.ERROR, (o) -> {
			tracker.setText(translation.getSingle("phase.analyze.error"));
			eventRegistryError(pipeline, o.b);
		});

		instance.addEventListener(RecordEvent.BEGIN, (o) -> {
			tracker.setText(translation.getSingle("phase.record.begin"));
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(RecordEvent.UPDATE, (o) -> {
			RecordTracker phaseTracker = (RecordTracker) o.b.getTracker();
			String progress = translation.getSingle("phase.record.update",
				"percent",      MathUtils.round(phaseTracker.getProgress() * 100.0, 2),
				"current_time", MathUtils.round(phaseTracker.getCurrentTime(), 2),
				"total_time",   MathUtils.round(phaseTracker.getTotalTime(), 2));
			tracker.setText(progress);
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(RecordEvent.END, (o) -> {
			tracker.setText(translation.getSingle("phase.record.end"));
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(RecordEvent.ERROR, (o) -> {
			tracker.setText(translation.getSingle("phase.record.error"));
			eventRegistryError(pipeline, o.b);
		});

		instance.addEventListener(PostProcessEvent.BEGIN, (o) -> {
			tracker.setText(translation.getSingle("phase.post_process.begin"));
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(PostProcessEvent.UPDATE, (o) -> {
			Tracker phaseTracker = o.b.getTracker();
			String progress = phaseTracker.getTextProgress();
			if(phaseTracker instanceof PostProcessTracker) {
				PostProcessTracker processTracker = (PostProcessTracker) phaseTracker;
				if(processTracker.getTotalTime() > 0.0) {
					progress = translation.getSingle("phase.post_process.update_percent",
						"name",         translation.getSingle("phase.post_process.enum." + processTracker.name()),
						"percent",      MathUtils.round(processTracker.getProgress() * 100.0, 2),
						"current_time", MathUtils.round(processTracker.getCurrentTime(), 2),
						"total_time",   MathUtils.round(processTracker.getTotalTime(), 2));
				} else {
					progress = translation.getSingle("phase.post_process.update_no_percent",
						"name",         translation.getSingle("phase.post_process.enum." + processTracker.name()),
						"current_time", MathUtils.round(processTracker.getCurrentTime(), 2));
				}
			} else if(phaseTracker instanceof EnumNameTracker) {
				EnumNameTracker processTracker = (EnumNameTracker) phaseTracker;
				progress = translation.getSingle("phase.post_process.enum." + processTracker.name());
			}
			tracker.setText(progress);
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(PostProcessEvent.END, (o) -> {
			tracker.setText(translation.getSingle("phase.post_process.end"));
			eventRegistryUpdate(pipeline);
		});
		instance.addEventListener(PostProcessEvent.ERROR, (o) -> {
			tracker.setText(translation.getSingle("phase.post_process.error"));
			eventRegistryError(pipeline, o.b);
		});
	}
	
	@SuppressWarnings("unchecked")
	private static final Listener<Object> eventRegitryUpdateListener(PipelineEventRegistry eventRegistry) {
		return (Listener<Object>) eventRegistry.getListeners(PipelineEvent.UPDATE).get(0);
	}
	
	@SuppressWarnings("unchecked")
	private static final Listener<Object> eventRegitryErrorListener(PipelineEventRegistry eventRegistry) {
		return (Listener<Object>) eventRegistry.getListeners(PipelineEvent.ERROR).get(0);
	}
	
	private final void runDownloadWidevineCDMProcessAndWait(DRMEventRegistry dummyEventRegistry) throws Exception {
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
			manager = new TrackerManager();
			tracker = new TextProgressSimpleTracker();
			manager.setTracker(tracker);
			PipelineEventRegistry eventRegistry = pipeline.getEventRegistry();
			listenerUpdate = eventRegitryUpdateListener(eventRegistry);
			listenerError = eventRegitryErrorListener(eventRegistry);
			// Obtain plugins translation
			Translation translation = IntegrationUtils.translation();
			// Ensure there is Widevine CDM installed and ready
			if(!WidevineCDM.isInstalled()) {
				// Run the download process in a new separate process since we can initialize
				// the JCEF/CEF system only once and that must be with Widevine CDM ready.
				DRMEventRegistry dummyEventRegistry = new DRMEventRegistry();
				addDefaultBootstrapListeners(dummyEventRegistry, pipeline, translation);
				runDownloadWidevineCDMProcessAndWait(dummyEventRegistry);
			}
			// Create the output file so that the "Show file" function works
			NIO.createFile(destination);
			// Configure the DRM
			DRMConfiguration configuration = new DRMConfiguration.Builder()
					.output(destination)
					.media(media)
					.detectFPS(false) // Do not automatically detect FPS for now
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
		
		private String text;
		
		public void setText(String text) {
			this.text = text;
		}
		
		@Override
		public double getProgress() {
			return Double.NaN;
		}
		
		@Override
		public String getTextProgress() {
			return text;
		}
	}
	
	private static final class DownloadWidevineCDMProcessLineParser implements Consumer<String> {
		
		private static Map<String, EventType<?, ?>> eventTypes;
		
		private final String prefix = DRMBootstrapCLI.linePrefix();
		private final DRMEventRegistry eventRegistry;
		
		private boolean isAccumulating;
		private StringBuilder accumulator;
		
		private TrackerManager trackerManager;
		private DummyDownloadTracker downloadTracker;
		
		private DownloadWidevineCDMProcessLineParser(DRMEventRegistry eventRegistry) {
			this.eventRegistry = eventRegistry;
		}
		
		@SuppressWarnings("unchecked")
		private static final <T extends IEventType, P> EventType<T, P> nameToEventType(Class<T> clazz, String name) {
			if(eventTypes == null) {
				eventTypes = new HashMap<>();
				for(Field field : clazz.getDeclaredFields()) {
					try {
						eventTypes.put(field.getName(), (EventType<?, ?>) field.get(null));
					} catch(Exception ex) {
						// Ignore
					}
				}
			}
			return (EventType<T, P>) eventTypes.get(name);
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
				EventType<WidevineCDMEvent, ?> eventType = nameToEventType(WidevineCDMEvent.class, name);
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
						trackerManager.setTracker(downloadTracker);
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
			public double getProgress() {
				return progress;
			}
		}
	}
}