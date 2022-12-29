package sune.app.mediadownloader.drm.phase;

import java.net.SocketException;
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
import sune.app.mediadown.event.tracker.TrackerEvent;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadown.util.OSUtils;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.StateMutex;
import sune.app.mediadown.util.Threads;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMConstants;
import sune.app.mediadownloader.drm.DRMContext;
import sune.app.mediadownloader.drm.DRMLog;
import sune.app.mediadownloader.drm.event.RecordEvent;
import sune.app.mediadownloader.drm.tracker.RecordTracker;
import sune.app.mediadownloader.drm.util.Cut;
import sune.app.mediadownloader.drm.util.PlaybackData;
import sune.app.mediadownloader.drm.util.PlaybackEventsHandler;
import sune.app.mediadownloader.drm.util.RealTimeAudio;
import sune.app.mediadownloader.drm.util.RealTimeAudio.AudioVolume;
import sune.app.mediadownloader.drm.util.RecordInfo;
import sune.app.mediadownloader.drm.util.RecordMetrics;
import sune.app.mediadownloader.drm.util.WindowsKill;

public class RecordPhase implements PipelineTask<RecordPhaseResult> {
	
	private static final Logger logger = DRMLog.get();
	private static final Pattern PATTERN_LINE_PROGRESS = Pattern.compile("^frame=\\s*(\\d+)\\s+fps=\\s*([^\\s]+)\\s+.*?time=([^\\s]+)\\s+.*$");
	
	private final DRMContext context;
	private final Path recordPath;
	private final double duration;
	private final double frameRate;
	private final int sampleRate;
	private final double audioOffset;
	
	private final RecordMetrics metrics = new RecordMetrics();
	private final StateMutex mtxDone = new StateMutex();
	private RecordTracker tracker;
	
	private final List<Cut.OfDouble> videoCuts = new ArrayList<>();
	private final List<Cut.OfDouble> audioCuts = new ArrayList<>();
	private boolean playbackEnded = false;
	private boolean recordPaused = false;
	private double startCutOff = 0.0;
	private double endCutOff = -1.0;
	private volatile double pauseTime = -1.0;
	private final AtomicBoolean videoPlaying = new AtomicBoolean(true);
	private final AtomicBoolean recordActive = new AtomicBoolean(false);
	
	private RealTimeAudio audio;
	private volatile double lastAudioTime = 0.0;
	
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
	
	private final void recordUpdated(String line) {
		if(logger.isDebugEnabled())
			logger.debug("FFMpeg | {}", line);
		
		Matcher matcher = PATTERN_LINE_PROGRESS.matcher(line);
		if(!matcher.matches()) return; // Ignore non-progress lines
		
		int recordFrames = Integer.valueOf(matcher.group(1));
		double recordFPS = Double.valueOf(matcher.group(2));
		double recordTime = Utils.convertToSeconds(matcher.group(3));
		
		if(!recordStarted) {
			// Notify the thread that is checking if an error happened
			threadInit.interrupt();
			recordStarted = true;
		}
		
		metrics.updateRecord(recordTime, recordFrames, recordFPS);
	}
	
	private final void audioUpdated(AudioVolume audioVolume) {
		if(audioVolume.volume() > DRMConstants.DEFAULT_SILENCE_THRESHOLD) {
			lastAudioTime = audioVolume.time();
		}
	}
	
	private synchronized void startRecord() throws Exception {
		recordActive.set(false);
		
		audio = new RealTimeAudio(DRMConstants.AUDIO_LISTEN_SERVER_PORT);
		audio.listen(this::audioUpdated);
		
		tracker = new RecordTracker(duration);
		TrackerManager manager = context.trackerManager();
		manager.tracker(tracker);
		manager.addEventListener(TrackerEvent.UPDATE, (t) -> context.eventRegistry().call(RecordEvent.UPDATE, new Pair<>(context, manager)));
		process = context.processManager().ffmpeg(this::recordUpdated);
		String windowTitle = context.browserContext().title();
		String audioDeviceName = context.audioDeviceName();
		String command = context.commandFactory().recordCommand(audioDeviceName, frameRate, sampleRate, windowTitle, audioOffset, recordPath);
		if(logger.isDebugEnabled())
			logger.debug("Record command: ffmpeg{}", command);
		process.execute(command);
		// Catch any FFMpeg startup errors (such as incorrect arguments, etc.)
		(threadInit = Threads.newThreadUnmanaged(() -> {
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
	
	private void pauseRecord(PlaybackData data) {
		if(process == null)
			throw new IllegalStateException("FFMpeg not ready");
		if(!videoPlaying.get()) return; // Video not playing
		
		// Get the current time as soon as possible in the method
		double recordTime = metrics.recordTime() - 0.5 / frameRate;
		
		if(logger.isDebugEnabled())
			logger.debug("Record paused");
		
		if(pauseTime < 0.0) {
			pauseTime = recordTime;
		}
		
		videoPlaying.set(false);
	}
	
	private void resumeRecord(PlaybackData data) {
		if(process == null)
			throw new IllegalStateException("FFMpeg not ready");
		if(videoPlaying.get()) return; // Video already playing
		
		// Get the current time as soon as possible in the method
		double recordTime = metrics.recordTime() - 0.5 / frameRate;
		
		if(logger.isDebugEnabled())
			logger.debug("Record resumed");
		
		if(pauseTime >= 0.0) {
			double startTime = pauseTime;
			double endTime = recordTime;
			Cut.OfDouble cutVideo = new Cut.OfDouble(startTime, endTime);
			videoCuts.add(cutVideo);
			
			if(logger.isDebugEnabled())
				logger.debug("Cut video: {}", cutVideo);
			
			double length = endTime - startTime;
			double lat = lastAudioTime;
			Cut.OfDouble cutAudio = new Cut.OfDouble(lat, lat + length);
			audioCuts.add(cutAudio);
			
			if(logger.isDebugEnabled())
				logger.debug("Cut audio: {}", cutAudio);
			
			pauseTime = -1.0;
		}
		videoPlaying.set(true);
	}
	
	private final void closeProcess() throws Exception {
		if(!recordActive.get()) return; // Already closed
		
		if(logger.isDebugEnabled())
			logger.debug("Sending quit command to the recording process...");
		
		Process p = process.process();
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
	
	private final void closeAudio() throws Exception {
		if(audio != null) {
			try {
				audio.close();
			} catch(SocketException ex) {
				// Ignore "Connection reset" exceptions
				if(!ex.getMessage().equals("Connection reset"))
					throw ex;
			}
		}
	}
	
	@Override
	public RecordPhaseResult run(Pipeline pipeline) throws Exception {
		running.set(true);
		started.set(true);
		context.eventRegistry().call(RecordEvent.BEGIN, context);
		try {
			context.playbackEventsHandler(new RecordPhaseHandler());
			
			if(logger.isDebugEnabled())
				logger.debug("Setting time to 0.0 seconds...");
			
			context.playback().time(0.0, true).then(() -> {
				if(logger.isDebugEnabled())
					logger.debug("Time set to 0.0 seconds. Starting recording...");
				
				(Threads.newThreadUnmanaged(() -> {
					try {
						StateMutex mtxAudio = new StateMutex();
						
						if(logger.isDebugEnabled())
							logger.debug("Unmuting the audio...");
						
						context.playback().unmute().then(() -> {
							if(logger.isDebugEnabled())
								logger.debug("Setting volume to max...");
							
							context.playback().volume(1.0).then(() -> {
								if(logger.isDebugEnabled())
									logger.debug("Audio unmuted and set to max.");
								
								mtxAudio.unlock();
							});
						});
						
						if(logger.isDebugEnabled())
							logger.debug("Waiting for the audio to be prepared...");
						
						mtxAudio.await();
						
						startRecord();
						
						if(logger.isDebugEnabled())
							logger.debug("Playing the video...");
						
						// Recording has been started, also start the video
						context.playback().play().then(() -> {
							if(logger.isDebugEnabled())
								logger.debug("Video played.");
						});
					} catch(Exception ex) {
						exception.set(ex);
						mtxDone.unlock();
					}
				})).start();
			});
			mtxDone.await();
			if(stopped.get()) return null; // Sending null will stop the pipeline
			// If any exception happened, just throw it
			Exception ex = exception.get();
			if(ex != null) throw ex;
			// If no exception was thrown, create the result
			RecordInfo recordInfo = new RecordInfo(recordPath, videoCuts, audioCuts, frameRate, sampleRate, audioOffset,
			                                       new Cut.OfDouble(startCutOff, endCutOff));
			return new RecordPhaseResult(context, recordInfo);
		} finally {
			running.set(false);
			
			if(!stopped.get()) {
				try {
					context.processManager().closeAll();
					closeAudio();
				} finally {
					done.set(true);
					context.eventRegistry().call(RecordEvent.END, context);
				}
			}
		}
	}
	
	@Override
	public void stop() throws Exception {
		if(!running.get()) return; // Do not continue
		running.set(false);
		
		try {
			context.processManager().stop();
			closeAudio();
		} finally {
			mtxDone.unlock();
			
			if(!done.get()) {
				stopped.set(true);
			}
			
			context.eventRegistry().call(RecordEvent.END, context);
		}
	}
	
	@Override
	public void pause() throws Exception {
		if(!running.get()) return; // Do not continue
		context.playback().pause();
		context.processManager().pause();
		running.set(false);
		paused .set(true);
		context.eventRegistry().call(RecordEvent.PAUSE, context);
	}
	
	@Override
	public void resume() throws Exception {
		if(running.get()) return; // Do not continue
		context.playback().play();
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
		
		private boolean isFirstEvent = true;
		
		private final void setStartCutOff(PlaybackData data) {
			startCutOff = metrics.startCutOff() - data.time;
			if(logger.isDebugEnabled())
				logger.debug("Start cut off: {}", startCutOff);
			isFirstEvent = false;
		}
		
		@Override
		public void updated(PlaybackData data) {
			if(!recordActive.get()) return;
			metrics.updatePlayback(data.time, data.frame);
			
			if(isFirstEvent)
				setStartCutOff(data);
			
			if(logger.isDebugEnabled())
				logger.debug("Update | time={}, frame={}, record time={}, buffered={}, fps={}", data.time, data.frame, metrics.recordTime(), data.buffered, metrics.playbackFPS());
			
			if(recordPaused) {
				resumeRecord(data);
			}
			
			tracker.update(data.time);
		}
		
		@Override
		public void waiting(PlaybackData data) {
			if(!recordActive.get()) return;
			metrics.updatePlayback(data.time, data.frame);
			if(recordPaused) return; // Not resumed, ignore
			
			if(isFirstEvent)
				setStartCutOff(data);
			
			if(logger.isDebugEnabled())
				logger.debug("Wait | time={}, frame={}, record time={}", data.time, data.frame, metrics.recordTime());
			
			if(!playbackEnded) pauseRecord(data);
			recordPaused = true;
		}
		
		@Override
		public void resumed(PlaybackData data) {
			if(!recordActive.get()) return;
			metrics.updatePlayback(data.time, data.frame);
			if(!recordPaused) return; // Not paused, ignore
			
			if(isFirstEvent)
				setStartCutOff(data);
			
			// Video is buffered (after wait), can be played
			if(logger.isDebugEnabled())
				logger.debug("Resume | time={}, frame={}, record time={}", data.time, data.frame, metrics.recordTime());
			
			resumeRecord(data);
			recordPaused = false;
		}
		
		@Override
		public void ended(PlaybackData data) {
			if(!recordActive.get()) return;
			metrics.updatePlayback(data.time, data.frame);
			
			if(isFirstEvent)
				setStartCutOff(data);
			
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
			} catch(Exception ex) {
				exception.set(ex);
			} finally {
				mtxDone.unlock();
			}
		}
	}
}