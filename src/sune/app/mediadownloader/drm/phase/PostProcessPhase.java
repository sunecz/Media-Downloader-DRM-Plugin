package sune.app.mediadownloader.drm.phase;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMContext;
import sune.app.mediadownloader.drm.DRMLog;
import sune.app.mediadownloader.drm.event.PostProcessEvent;
import sune.app.mediadownloader.drm.integration.DRMPluginConfiguration;
import sune.app.mediadownloader.drm.process.SimpleAudioProcessor;
import sune.app.mediadownloader.drm.process.SimpleVideoProcessor;
import sune.app.mediadownloader.drm.tracker.PostProcessTracker;
import sune.app.mediadownloader.drm.util.DRMProcessUtils;
import sune.app.mediadownloader.drm.util.DRMUtils;
import sune.app.mediadownloader.drm.util.FFMpegTimeProgressParser;
import sune.app.mediadownloader.drm.util.FilesManager;
import sune.app.mediadownloader.drm.util.ProcessManager;
import sune.app.mediadownloader.drm.util.RecordInfo;

public class PostProcessPhase implements PipelineTask<PostProcessPhaseResult> {
	
	private static final Logger logger = DRMLog.get();
	
	private final DRMContext context;
	private final RecordInfo recordInfo;
	
	private final AtomicBoolean running = new AtomicBoolean();
	private final AtomicBoolean started = new AtomicBoolean();
	private final AtomicBoolean done = new AtomicBoolean();
	private final AtomicBoolean paused = new AtomicBoolean();
	private final AtomicBoolean stopped = new AtomicBoolean();
	
	public PostProcessPhase(DRMContext context, RecordInfo recordInfo) {
		this.context = context;
		this.recordInfo = recordInfo;
	}
	
	private final double postProcessVideo(ProcessManager processManager, FilesManager filesManager,
			Path inputPath, Path outputPath) throws Exception {
		SimpleVideoProcessor processor
			= new SimpleVideoProcessor(context.trackerManager(), processManager, filesManager, recordInfo,
			                           inputPath, outputPath);
		processor.process();
		return processor.duration();
	}
	
	private final double postProcessAudio(ProcessManager processManager, FilesManager filesManager,
			Path inputPath, Path outputPath) throws Exception {
		SimpleAudioProcessor processor
			= new SimpleAudioProcessor(context.trackerManager(), processManager, filesManager, recordInfo,
			                           inputPath, outputPath);
		processor.process();
		return processor.duration();
	}
	
	private final void postProcess() throws Exception {
		TrackerManager trackerManager = context.trackerManager();
		trackerManager.setUpdateListener(() -> context.eventRegistry().call(PostProcessEvent.UPDATE, new Pair<>(context, trackerManager)));
		
		if(logger.isDebugEnabled())
			logger.debug("Post-processing...");
		
		if(logger.isDebugEnabled())
			logger.debug("Closing browser...");
		
		context.browserContext().close();
		
		if(logger.isDebugEnabled())
			logger.debug("Browser closed.");
		
		Path output = context.configuration().output();
		Path outputRecord = recordInfo.path();
		
		String fileName = output.getFileName().toString();
		Path videoOutputPath = output.resolveSibling(fileName + ".video.mkv");
		Path audioOutputPath = output.resolveSibling(fileName + ".audio.wav");
		
		ProcessManager processManager = context.processManager();
		FilesManager filesManager = new FilesManager();
		double durationVideo = postProcessVideo(processManager, filesManager, outputRecord, videoOutputPath);
		double durationAudio = postProcessAudio(processManager, filesManager, outputRecord, audioOutputPath);
		if(processManager.isStopped()) return; // Stopped, do not continue
		
		double duration = Math.min(durationVideo, durationAudio);
		PostProcessTracker.Factory<PostProcessOperation> processTrackerFactory
			= new PostProcessTracker.Factory<>(PostProcessOperation.class);
		PostProcessTracker tracker = processTrackerFactory.create(duration, PostProcessOperation.MERGE);
		trackerManager.setTracker(tracker);
		trackerManager.update();
		
		Consumer<String> parser = new FFMpegTimeProgressParser(tracker);
		try(ReadOnlyProcess process = processManager.ffmpeg(parser)) {
			StringBuilder builder = new StringBuilder();
			builder.append(" -y -hide_banner -v info");
			builder.append(" -i \"%{input_video}s\"");
			builder.append(" -itsoffset %{audio_offset}s"); // Fix video/audio desync
			builder.append(" -i \"%{input_audio}s\"");
			builder.append(" -c:v copy -c:a aac");
			builder.append(" -shortest"); // Fix output file duration
			builder.append(" \"%{output}s\"");
			String command = Utils.format(builder.toString(),
				"input_video", videoOutputPath.toAbsolutePath().toString(),
				"audio_offset", DRMUtils.toString(recordInfo.audioOffset()),
				"input_audio", audioOutputPath.toAbsolutePath().toString(),
				"output", output.toAbsolutePath().toString());
			if(logger.isDebugEnabled())
				logger.debug("ffmpeg{}", command);
			process.execute(command);
			DRMProcessUtils.throwIfFailedFFMpeg(process.waitFor());
		}
		
		DRMPluginConfiguration configuration = DRMPluginConfiguration.instance();
		
		if(!configuration.processKeepTemporaryFiles()) {
			filesManager.delete(videoOutputPath);
			filesManager.delete(audioOutputPath);
		}
		
		if(!configuration.processKeepRecordFile()) {
			filesManager.delete(outputRecord);
		}
		
		filesManager.deleteAll();
	}
	
	@Override
	public PostProcessPhaseResult run(Pipeline pipeline) throws Exception {
		running.set(true);
		started.set(true);
		context.eventRegistry().call(PostProcessEvent.BEGIN, context);
		try {
			postProcess();
			return new PostProcessPhaseResult(context);
		} finally {
			running.set(false);
			if(!stopped.get()) {
				done.set(true);
				context.eventRegistry().call(PostProcessEvent.END, context);
			}
		}
	}
	
	@Override
	public void stop() throws Exception {
		if(!running.get()) return; // Do not continue
		running.set(false);
		context.processManager().stop();
		if(!done.get()) stopped.set(true);
		context.eventRegistry().call(PostProcessEvent.END, context);
	}
	
	@Override
	public void pause() throws Exception {
		if(!running.get()) return; // Do not continue
		context.processManager().pause();
		running.set(false);
		paused .set(true);
		context.eventRegistry().call(PostProcessEvent.PAUSE, context);
	}
	
	@Override
	public void resume() throws Exception {
		if(running.get()) return; // Do not continue
		context.processManager().resume();
		paused .set(false);
		running.set(true);
		context.eventRegistry().call(PostProcessEvent.RESUME, context);
	}
	
	@Override
	public final boolean isRunning() {
		return running.get();
	}
	
	@Override
	public final boolean isStarted() {
		return started.get();
	}
	
	@Override
	public final boolean isDone() {
		return done.get();
	}
	
	@Override
	public final boolean isPaused() {
		return paused.get();
	}
	
	@Override
	public final boolean isStopped() {
		return stopped.get();
	}
	
	private static enum PostProcessOperation {
		
		MERGE;
	}
}