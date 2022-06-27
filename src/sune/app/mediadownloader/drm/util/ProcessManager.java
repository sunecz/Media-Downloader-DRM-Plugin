package sune.app.mediadownloader.drm.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.ffmpeg.FFMpeg;
import sune.app.mediadown.util.ProcessUtils;

public final class ProcessManager {
	
	private ReadOnlyProcess process;
	
	private final AtomicBoolean paused = new AtomicBoolean();
	private final AtomicBoolean stopped = new AtomicBoolean();
	
	public ReadOnlyProcess ffmpeg(Consumer<String> listener) throws Exception {
		if(process != null) process.waitFor(); // Wait for previous process to finish
		process = FFMpeg.createAsynchronousProcess(listener);
		return process;
	}
	
	public final void stop() throws Exception {
		if(stopped.get() || process == null || process.getProcess() == null)
			return;
		process.close();
		stopped.set(true);
	}
	
	public final void pause() throws Exception {
		if(paused.get() || process == null || process.getProcess() == null)
			return;
		ProcessUtils.pause(process.getProcess());
		paused.set(true);
	}
	
	public final void resume() throws Exception {
		if(!paused.get() || process == null || process.getProcess() == null)
			return;
		ProcessUtils.resume(process.getProcess());
		paused.set(false);
	}
	
	public final ReadOnlyProcess process() {
		return process;
	}
	
	public final boolean isPaused() {
		return paused.get();
	}
	
	public final boolean isStopped() {
		return stopped.get();
	}
}