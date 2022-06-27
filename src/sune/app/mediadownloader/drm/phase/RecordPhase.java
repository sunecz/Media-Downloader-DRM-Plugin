package sune.app.mediadownloader.drm.phase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadown.util.OSUtils;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMContext;
import sune.app.mediadownloader.drm.DRMLog;
import sune.app.mediadownloader.drm.event.RecordEvent;
import sune.app.mediadownloader.drm.tracker.RecordTracker;
import sune.app.mediadownloader.drm.util.Cut;
import sune.app.mediadownloader.drm.util.DRMUtils;
import sune.app.mediadownloader.drm.util.PlaybackData;
import sune.app.mediadownloader.drm.util.PlaybackEventsHandler;
import sune.app.mediadownloader.drm.util.RecordInfo;
import sune.app.mediadownloader.drm.util.RecordMetrics;
import sune.app.mediadownloader.drm.util.StateMutex;
import sune.app.mediadownloader.drm.util.WindowsKill;

public class RecordPhase implements PipelineTask<RecordPhaseResult> {
	
	private static final Logger logger = DRMLog.get();
	
	private static final Pattern PATTERN_LINE_PROGRESS = Pattern.compile("^frame=\\s*(\\d+)\\s+fps=\\s*(\\d+)\\s.*?time=(.*?)\\s.*$");
	
	private final DRMContext context;
	private final Path recordPath;
	private final double duration;
	private final double frameRate;
	private final int sampleRate;
	private final double audioOffset;
	
	private final RecordMetrics metrics = new RecordMetrics();
	private final StateMutex mtxDone = new StateMutex();
	private RecordTracker tracker;
	
	private final List<Cut.OfDouble> cuts = new ArrayList<>();
	private boolean playbackEnded = false;
	private boolean recordPaused = false;
	private double startCutOff = 0.0;
	private double endCutOff = -1.0;
	private double pauseTime = -1.0;
	private final AtomicBoolean videoPlaying = new AtomicBoolean(true);
	private final AtomicBoolean recordActive = new AtomicBoolean(false);
	
	// Variables used for initializing the record process
	private Thread threadInit;
	private final AtomicReference<Exception> exception = new AtomicReference<>();
	private final StateMutex mtxRecordStart = new StateMutex();
	private boolean recordStarted = false;
	private ReadOnlyProcess process;
	
	private final AtomicBoolean running = new AtomicBoolean();
	private final AtomicBoolean started = new AtomicBoolean();
	private final AtomicBoolean done = new AtomicBoolean();
	private final AtomicBoolean paused = new AtomicBoolean();
	private final AtomicBoolean stopped = new AtomicBoolean();
	
	public RecordPhase(DRMContext context, double duration, double frameRate, int sampleRate,
			double audioOffset) {
		this.context = context;
		this.recordPath = ensureMKVPath(context.configuration().output());
		this.duration = duration;
		this.frameRate = frameRate;
		this.sampleRate = sampleRate;
		this.audioOffset = audioOffset;
	}
	
	private static final Path ensureMKVPath(Path path) {
		String fileName = path.getFileName().toString();
		if(Utils.fileType(fileName).equalsIgnoreCase("mkv")) return path; // Is MKV
		return path.resolveSibling(Utils.fileNameNoType(fileName) + ".mkv");
	}
	
	private final void ffmpegOutputHandler(String line) {
		if(logger.isDebugEnabled())
			logger.debug("FFMpeg | {}", line);
		Matcher matcher = PATTERN_LINE_PROGRESS.matcher(line);
		if(!matcher.matches()) return; // Ignore non-progress lines
		int recordFrames = Integer.valueOf(matcher.group(1));
		double recordFPS = Integer.valueOf(matcher.group(2));
		double recordTime = Utils.convertToSeconds(matcher.group(3));
		if(!recordStarted) {
			// Notify the thread that is checking if an error happened
			threadInit.interrupt();
			recordStarted = true;
		}
		metrics.updateRecord(recordTime, recordFrames, recordFPS);
	}
	
	private final String getRecordFFMpegCommand(String audioDeviceName, double frameRate, int sampleRate, String windowTitle) {
		StringBuilder builder = new StringBuilder();
		builder.append(" -y"); // Rewrite the output file, if it exists
		builder.append(" -f dshow"); // Record audio
		builder.append(" -thread_queue_size 1024 -probesize 8M -channels 2 -sample_rate %{sample_rate}d -channel_layout stereo"); // Input audio settings
		builder.append(" -itsoffset " + audioOffset); // Fix video/audio desync
		builder.append(" -i audio=\"%{audio_device_name}s\""); // Record specific audio input
		builder.append(" -f gdigrab"); // Record video
		builder.append(" -thread_queue_size 1024 -probesize 64M -fflags +igndts -framerate %{frame_rate}s -draw_mouse 0"); // Input video settings
		builder.append(" -i title=\"%{window_title}s\""); // Record specific window
		builder.append(" -c:v libx264rgb -r %{frame_rate}s"); // Output video settings
		builder.append(" -c:a pcm_s16le -ac 2 -ar %{sample_rate}d -channel_layout stereo"); // Output audio settings
		builder.append(" -preset ultrafast -tune zerolatency -crf 18 -qp 0 -pix_fmt yuv420p"); // Performance settings
		builder.append(" -af asetpts=N/SR/TB -vf setpts=N/FR/TB"); // Ensure correct timestamps on pause/resume
		builder.append(" -hide_banner -loglevel warning -stats -stats_period %{progress_interval}s");
		builder.append(" \"%{output}s\"");
		String command = Utils.format(builder.toString(),
			"audio_device_name", audioDeviceName,
			"sample_rate", sampleRate,
			"window_title", windowTitle,
			"frame_rate", DRMUtils.toString(frameRate),
			"output", recordPath.toAbsolutePath().toString(),
			"progress_interval", DRMUtils.toString(1.0 / frameRate));
		return command;
	}
	
	private synchronized void startRecord() throws Exception {
		recordActive.set(false);
		tracker = new RecordTracker(duration);
		TrackerManager manager = context.trackerManager();
		manager.setTracker(tracker);
		manager.setUpdateListener(() -> context.eventRegistry().call(RecordEvent.UPDATE, new Pair<>(context, manager)));
		process = context.processManager().ffmpeg(this::ffmpegOutputHandler);
		String audioDeviceName = context.audioDeviceName();
		String windowTitle = context.browserContext().title();
		String command = getRecordFFMpegCommand(audioDeviceName, frameRate, sampleRate, windowTitle);
		if(logger.isDebugEnabled())
			logger.debug("Record command: ffmpeg{}", command);
		process.execute(command);
		// Catch any FFMpeg startup errors (such as incorrect arguments, etc.)
		(threadInit = new Thread(() -> {
			try {
				int code = process.waitFor();
				// Process exited early with an error
				if(code != 0) {
					exception.set(new IllegalStateException("FFMpeg exited with error code " + code));
				}
			} catch(InterruptedException ex) {
				// The recording is running normally
				recordActive.set(true);
			} catch(Exception ex) {
				// Some other exception happened
				exception.set(ex);
			} finally {
				// Notify the waiting threads
				mtxRecordStart.unlock();
			}
		})).start();
		
		if(logger.isDebugEnabled())
			logger.debug("Waiting for initialization to finish...");
		
		// Wait for the initialization status to finish
		mtxRecordStart.await();
		
		if(logger.isDebugEnabled())
			logger.debug("Initialization done, has exception=" + (exception.get() != null));
		
		// Throw thrown exception, if any
		Exception ex = exception.getAndSet(null);
		if(ex != null) throw ex;
	}
	
	private void pauseRecord() {
		if(process == null)
			throw new IllegalStateException("FFMpeg not ready");
		if(!videoPlaying.get()) return; // Video not playing
		if(logger.isDebugEnabled())
			logger.debug("Record paused");
		if(pauseTime < 0.0) {
			pauseTime = metrics.recordTime();
		}
		videoPlaying.set(false);
	}
	
	private void resumeRecord() {
		if(process == null)
			throw new IllegalStateException("FFMpeg not ready");
		if(videoPlaying.get()) return; // Video already playing
		if(logger.isDebugEnabled())
			logger.debug("Record resumed");
		if(pauseTime >= 0.0) {
			cuts.add(new Cut.OfDouble(pauseTime, metrics.recordTime()));
			pauseTime = -1.0;
		}
		videoPlaying.set(true);
	}
	
	private final void closeProcess() throws Exception {
		if(!recordActive.get()) return; // Already closed
		
		if(logger.isDebugEnabled())
			logger.debug("Sending quit command to the recording process...");
		
		Process p = process.getProcess();
		// Gracefully quit the recording process
		byte[] cmdQuit = "q".getBytes();
		p.getOutputStream().write(cmdQuit);
		p.getOutputStream().flush();
		
		if(logger.isDebugEnabled())
			logger.debug("Waiting 5 seconds...");
		
		boolean exited = p.waitFor(5, TimeUnit.SECONDS);
		
		if(!exited) {
			if(logger.isDebugEnabled())
				logger.debug("Process not exited. Interrupting the recording process...");
			// Windows-only for now
			if(OSUtils.isWindows()) {
				// Interrupt the process, so the file is saved properly
				for(long pid = p.pid(); p.isAlive();) {
					WindowsKill.interrupt(pid);
				}
			}
			process.close();
		}
		
		if(logger.isDebugEnabled())
			logger.debug("Process {} closed.", exited ? "gracefully" : "forcibly");
		
		process = null;
		recordActive.set(false);
	}
	
	@Override
	public RecordPhaseResult run(Pipeline pipeline) throws Exception {
		running.set(true);
		started.set(true);
		context.eventRegistry().call(RecordEvent.BEGIN, context);
		try {
			context.playbackEventsHandler(new RecordPhaseHandler());
			context.playbackController().setTime(0.0, () -> {
				new Thread(() -> {
					try {
						startRecord();
						// Recording has been started, also start the video
						context.playbackController().play(() -> {
							startCutOff = metrics.startCutOff();
							if(logger.isDebugEnabled())
								logger.debug("Start cut off: {}", startCutOff);
						});
					} catch(Exception ex) {
						exception.set(ex);
						mtxDone.unlock();
					}
				}).start();
			});
			mtxDone.await();
			if(stopped.get()) return null; // Sending null will stop the pipeline
			// If any exception happened, just throw it
			Exception ex = exception.get();
			if(ex != null) throw ex;
			// If no exception was thrown, create the result
			RecordInfo recordInfo = new RecordInfo(recordPath, cuts, frameRate, sampleRate, audioOffset,
			                                       startCutOff, endCutOff);
			return new RecordPhaseResult(context, recordInfo);
		} finally {
			running.set(false);
			if(!stopped.get()) {
				done.set(true);
				context.eventRegistry().call(RecordEvent.END, context);
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
		context.eventRegistry().call(RecordEvent.END, context);
	}
	
	@Override
	public void pause() throws Exception {
		if(!running.get()) return; // Do not continue
		context.playbackController().pause();
		context.processManager().pause();
		running.set(false);
		paused .set(true);
		context.eventRegistry().call(RecordEvent.PAUSE, context);
	}
	
	@Override
	public void resume() throws Exception {
		if(running.get()) return; // Do not continue
		context.playbackController().play();
		context.processManager().resume();
		paused .set(false);
		running.set(true);
		context.eventRegistry().call(RecordEvent.RESUME, context);
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
	
	private final class RecordPhaseHandler implements PlaybackEventsHandler {
		
		@Override
		public void updated(PlaybackData data) {
			if(!recordActive.get()) return;
			metrics.updatePlayback(data.time, data.frame);
			
			if(logger.isDebugEnabled())
				logger.debug("Update | time={}, frame={}, record time={}, buffered={}, fps={}", data.time, data.frame, metrics.recordTime(), data.buffered, metrics.playbackFPS());
			
			if(recordPaused) {
				resumeRecord();
			}
			
			tracker.update(data.time);
		}
		
		@Override
		public void waiting(PlaybackData data) {
			if(!recordActive.get()) return;
			metrics.updatePlayback(data.time, data.frame);
			if(recordPaused) return; // Not resumed, ignore
			if(logger.isDebugEnabled())
				logger.debug("Wait | time={}, frame={}, record time={}", data.time, data.frame, metrics.recordTime());
			if(!playbackEnded) pauseRecord();
			recordPaused = true;
		}
		
		@Override
		public void resumed(PlaybackData data) {
			if(!recordActive.get()) return;
			metrics.updatePlayback(data.time, data.frame);
			if(!recordPaused) return; // Not paused, ignore
			// Video is buffered (after wait), can be played
			if(logger.isDebugEnabled())
				logger.debug("Resume | time={}, frame={}, record time={}", data.time, data.frame, metrics.recordTime());
			resumeRecord();
			recordPaused = false;
		}
		
		@Override
		public void ended(PlaybackData data) {
			if(!recordActive.get()) return;
			metrics.updatePlayback(data.time, data.frame);
			
			// If the video stopped while waiting (it can happen), use the pause time
			endCutOff = pauseTime >= 0.0 ? pauseTime : metrics.recordTime();
			pauseTime = -1.0;
			
			if(logger.isDebugEnabled())
				logger.debug("endCutOff: {}", endCutOff);
			
			playbackEnded = true;
			if(logger.isDebugEnabled())
				logger.debug("Stop | time={}, frame={}, record time={}", data.time, data.frame, metrics.recordTime());
			
			try {
				if(logger.isDebugEnabled())
					logger.debug("Closing record process...");
				closeProcess();
				mtxDone.unlock();
			} catch(Exception ex) {
				exception.set(ex);
			} finally {
				mtxDone.unlock();
			}
		}
	}
}