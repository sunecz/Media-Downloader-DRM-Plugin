package sune.app.mediadownloader.drm.phase;

import static sune.app.mediadownloader.drm.DRMConstants.AUDIO_BUFFER_SIZE_MS;
import static sune.app.mediadownloader.drm.DRMConstants.AUDIO_MAX_LATENCY_SAMPLES;
import static sune.app.mediadownloader.drm.DRMConstants.AUDIO_OUTPUT_SAMPLE_RATE;
import static sune.app.mediadownloader.drm.DRMConstants.MS_IN_SEC;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadown.util.Pair;
import sune.app.mediadownloader.drm.DRMConstants;
import sune.app.mediadownloader.drm.DRMContext;
import sune.app.mediadownloader.drm.DRMLog;
import sune.app.mediadownloader.drm.event.AnalyzeEvent;
import sune.app.mediadownloader.drm.tracker.AnalyzeTracker;
import sune.app.mediadownloader.drm.util.PlaybackData;
import sune.app.mediadownloader.drm.util.PlaybackEventsHandler;
import sune.app.mediadownloader.drm.util.RecordMetrics;
import sune.app.mediadownloader.drm.util.StateMutex;

public class AnalyzePhase implements PipelineTask<AnalyzePhaseResult> {
	
	private static final Logger logger = DRMLog.get();
	
	private final DRMContext context;
	private final double duration;
	private final double analyzeDuration;
	
	private final RecordMetrics metrics = new RecordMetrics();
	private final StateMutex mtxDone = new StateMutex();
	private boolean analyzeBuffered = false;
	private AnalyzeTracker tracker;
	
	private final AtomicBoolean running = new AtomicBoolean();
	private final AtomicBoolean started = new AtomicBoolean();
	private final AtomicBoolean done = new AtomicBoolean();
	private final AtomicBoolean paused = new AtomicBoolean();
	private final AtomicBoolean stopped = new AtomicBoolean();

	public AnalyzePhase(DRMContext context, double duration, double analyzeDuration) {
		this.context = context;
		this.duration = duration;
		this.analyzeDuration = analyzeDuration;
	}
	
	private static final double calculateAudioOffset() {
		return (-2.0 * AUDIO_MAX_LATENCY_SAMPLES) / AUDIO_OUTPUT_SAMPLE_RATE - AUDIO_BUFFER_SIZE_MS / MS_IN_SEC;
	}
	
	@Override
	public AnalyzePhaseResult run(Pipeline pipeline) throws Exception {
		running.set(true);
		started.set(true);
		context.eventRegistry().call(AnalyzeEvent.BEGIN, context);
		try {
			double audioOffset = calculateAudioOffset();
			int sampleRate = DRMConstants.AUDIO_OUTPUT_SAMPLE_RATE;
			double frameRate = DRMConstants.DEFAULT_FRAMERATE;
			if(context.configuration().detectFPS()) {
				tracker = new AnalyzeTracker(analyzeDuration);
				TrackerManager manager = context.trackerManager();
				manager.setTracker(tracker);
				manager.setUpdateListener(() -> context.eventRegistry().call(AnalyzeEvent.UPDATE, new Pair<>(context, manager)));
				context.playbackEventsHandler(new AnalyzePhaseHandler());
				context.playback().play();
				mtxDone.await();
				if(stopped.get()) return null; // Sending null will stop the pipeline
				frameRate = metrics.playbackFPS();
			}
			if(logger.isDebugEnabled())
				logger.debug("Analyzed FPS: " + frameRate);
			return new AnalyzePhaseResult(context, duration, frameRate, sampleRate, audioOffset);
		} finally {
			running.set(false);
			if(!stopped.get()) {
				done.set(true);
				context.eventRegistry().call(AnalyzeEvent.END, context);
			}
		}
	}
	
	@Override
	public void stop() throws Exception {
		if(!running.get()) return; // Do not continue
		running.set(false);
		context.processManager().stop();
		mtxDone.unlock();
		if(!done.get()) stopped.set(true);
		context.eventRegistry().call(AnalyzeEvent.END, context);
	}
	
	@Override
	public void pause() throws Exception {
		if(!running.get()) return; // Do not continue
		context.playback().pause();
		context.processManager().pause();
		running.set(false);
		paused .set(true);
		context.eventRegistry().call(AnalyzeEvent.PAUSE, context);
	}
	
	@Override
	public void resume() throws Exception {
		if(running.get()) return; // Do not continue
		context.playback().play();
		context.processManager().resume();
		paused .set(false);
		running.set(true);
		context.eventRegistry().call(AnalyzeEvent.RESUME, context);
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
	
	private final class AnalyzePhaseHandler implements PlaybackEventsHandler {
		
		@Override
		public void updated(PlaybackData data) {
			metrics.updatePlayback(data.time, data.frame);
			if(logger.isDebugEnabled())
				logger.debug("Analyze update | time={}, frame={}, buffered={}, fps={}", data.time, data.frame, data.buffered, metrics.playbackFPS());
			tracker.setIsBuffering(!analyzeBuffered);
			tracker.update(data.time);
			if(analyzeBuffered) {
				if(logger.isDebugEnabled())
					logger.debug("Analyze measuring");
				if(data.time >= analyzeDuration) {
					context.playback().pause().then(() -> {
						mtxDone.unlock();
					});
				}
			} else {
				if(data.buffered >= analyzeDuration) {
					if(logger.isDebugEnabled())
						logger.debug("Analyze buffered");
					analyzeBuffered = true;
					metrics.reset();
					context.playback().pause().then(() -> {
						context.playback().time(0.0, true).then(() -> {
							context.playback().play();
						});
					});
				}
			}
		}
		
		@Override
		public void waiting(PlaybackData data) {
			// Do nothing
		}
		
		@Override
		public void resumed(PlaybackData data) {
			// Do nothing
		}
		
		@Override
		public void ended(PlaybackData data) {
			// Do nothing
		}
	}
}