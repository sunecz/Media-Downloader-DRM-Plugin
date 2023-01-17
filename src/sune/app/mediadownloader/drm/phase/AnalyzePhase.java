package sune.app.mediadownloader.drm.phase;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import sune.app.mediadown.event.tracker.TrackerEvent;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.media.Media;
import sune.app.mediadown.media.MediaType;
import sune.app.mediadown.media.VideoMediaBase;
import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.StateMutex;
import sune.app.mediadownloader.drm.DRMConfiguration;
import sune.app.mediadownloader.drm.DRMConstants;
import sune.app.mediadownloader.drm.DRMContext;
import sune.app.mediadownloader.drm.DRMLog;
import sune.app.mediadownloader.drm.event.AnalyzeEvent;
import sune.app.mediadownloader.drm.tracker.AnalyzeTracker;
import sune.app.mediadownloader.drm.util.Environment;
import sune.app.mediadownloader.drm.util.PlaybackData;
import sune.app.mediadownloader.drm.util.PlaybackEventsHandler;
import sune.app.mediadownloader.drm.util.RecordMetrics;

public class AnalyzePhase implements PipelineTask<AnalyzePhaseResult> {
	
	private static final Logger logger = DRMLog.get();
	
	private final DRMContext context;
	private final double duration;
	
	private final RecordMetrics metrics = new RecordMetrics();
	private final StateMutex mtxDone = new StateMutex();
	private boolean analyzeBuffered = false;
	private AnalyzeTracker tracker;
	
	private final AtomicBoolean running = new AtomicBoolean();
	private final AtomicBoolean started = new AtomicBoolean();
	private final AtomicBoolean done = new AtomicBoolean();
	private final AtomicBoolean paused = new AtomicBoolean();
	private final AtomicBoolean stopped = new AtomicBoolean();

	public AnalyzePhase(DRMContext context, double duration) {
		this.context = context;
		this.duration = duration;
	}
	
	@Override
	public AnalyzePhaseResult run(Pipeline pipeline) throws Exception {
		running.set(true);
		started.set(true);
		
		try {
			context.eventRegistry().call(AnalyzeEvent.BEGIN, context);
			
			DRMConfiguration configuration = context.configuration();
			double recordFrameRate = DRMConstants.DEFAULT_FRAMERATE;
			double outputFrameRate = DRMConstants.DEFAULT_FRAMERATE;
			int sampleRate = DRMConstants.AUDIO_OUTPUT_SAMPLE_RATE;
			
			double configRecordFrameRate = configuration.recordFrameRate();
			if(configRecordFrameRate > 0.0) {
				recordFrameRate = configRecordFrameRate;
				
				if(logger.isDebugEnabled()) {
					logger.debug("Record frame rate set from configuration (frameRate={}).", recordFrameRate);
				}
			}
			
			boolean configUseDisplayRefreshRate = configuration.recordUseDisplayRefreshRate();
			if(configUseDisplayRefreshRate) {
				double displayRefreshRate = Environment.instance().displayRefreshRate();
				
				if(logger.isDebugEnabled()) {
					logger.debug("Detected display refresh rate: {} Hz.", displayRefreshRate);
				}
				
				if(displayRefreshRate > 0.0) {
					recordFrameRate = displayRefreshRate;
					
					if(logger.isDebugEnabled()) {
						logger.debug("Record frame rate set from display refresh rate (frameRate={}).", recordFrameRate);
					}
				}
			}
			
			double configOutputFrameRate = configuration.outputFrameRate();
			if(configOutputFrameRate > 0.0) {
				outputFrameRate = configOutputFrameRate;
				
				if(logger.isDebugEnabled()) {
					logger.debug("Output frame rate set from configuration (frameRate={}).", outputFrameRate);
				}
			}
			
			boolean configUseMediaFrameRate = configuration.outputUseMediaFrameRate();
			if(configUseMediaFrameRate) {
				Media media = configuration.media();
				VideoMediaBase video = Media.findOfType(media, MediaType.VIDEO);
				double mediaFrameRate = video.frameRate();
				
				if(mediaFrameRate > 0.0) {
					outputFrameRate = mediaFrameRate;
					
					if(logger.isDebugEnabled()) {
						logger.debug("Output frame rate set from media (frameRate={}).", outputFrameRate);
					}
				}
			}
			
			if(configuration.detectFrameRate()) {
				double analyzeDuration = configuration.analyzeDuration();
				tracker = new AnalyzeTracker(analyzeDuration);
				TrackerManager manager = context.trackerManager();
				manager.tracker(tracker);
				manager.addEventListener(TrackerEvent.UPDATE, (t) -> context.eventRegistry().call(AnalyzeEvent.UPDATE, new Pair<>(context, manager)));
				context.playbackEventsHandler(new AnalyzePhaseHandler(analyzeDuration));
				context.playback().play();
				mtxDone.await();
				if(stopped.get()) return null; // Sending null will stop the pipeline
				
				double analyzedFrameRate = metrics.playbackFrameRate();
				if(analyzedFrameRate > 0.0) {
					outputFrameRate = analyzedFrameRate;
					
					if(logger.isDebugEnabled()) {
						logger.debug("Output frame rate set from analysis (frameRate={}).", outputFrameRate);
					}
				}
			}
			
			if(logger.isDebugEnabled()) {
				logger.debug(
					"Final settings: recordFrameRate={}, outputFrameRate={}, sampleRate={}.",
					recordFrameRate, outputFrameRate, sampleRate
				);
			}
			
			return new AnalyzePhaseResult(context, duration, recordFrameRate, outputFrameRate, sampleRate);
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
		
		private final double analyzeDuration;
		
		public AnalyzePhaseHandler(double analyzeDuration) {
			this.analyzeDuration = analyzeDuration;
		}
		
		@Override
		public void updated(PlaybackData data) {
			metrics.updatePlayback(data.time, data.frame);
			
			if(logger.isDebugEnabled()) {
				logger.debug(
					"Analyze update | time={}, frame={}, buffered={}, frameRate={}",
					data.time, data.frame, data.buffered, metrics.playbackFrameRate()
				);
			}
			
			tracker.setIsBuffering(!analyzeBuffered);
			tracker.update(data.time);
			
			if(analyzeBuffered) {
				if(logger.isDebugEnabled()) {
					logger.debug("Analyze measuring");
				}
				
				if(data.time >= analyzeDuration) {
					context.playback().pause().then(() -> {
						mtxDone.unlock();
					});
				}
			} else {
				if(data.buffered >= analyzeDuration) {
					if(logger.isDebugEnabled()) {
						logger.debug("Analyze buffered");
					}
					
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