package sune.app.mediadownloader.drm.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.ffmpeg.FFMpeg;
import sune.app.mediadown.util.CheckedConsumer;
import sune.app.mediadown.util.ProcessUtils;

public final class ProcessManager {
	
	private final List<ReadOnlyProcess> processes = new ArrayList<>();
	private final AtomicBoolean paused = new AtomicBoolean();
	private final AtomicBoolean stopped = new AtomicBoolean();
	
	public ReadOnlyProcess ffmpeg(Consumer<String> listener) throws Exception {
		ReadOnlyProcess process = FFMpeg.createAsynchronousProcess(listener);
		processes.add(process);
		return process;
	}
	
	private final void doWithProcesses(CheckedConsumer<ReadOnlyProcess> action) throws Exception {
		Exception exception = null;
		for(ReadOnlyProcess process : processes) {
			if(process.process() == null)
				continue;
			try {
				action.accept(process);
			} catch(Exception ex) {
				exception = ex;
			}
		}
		if(exception != null)
			throw exception;
	}
	
	public final void closeAll() throws Exception {
		if(processes.isEmpty()) return;
		doWithProcesses(ReadOnlyProcess::close);
		processes.clear();
	}
	
	public final void stop() throws Exception {
		if(stopped.get()) return;
		closeAll();
		stopped.set(true);
	}
	
	public final void pause() throws Exception {
		if(paused.get()) return;
		doWithProcesses((p) -> ProcessUtils.pause(p.process()));
		paused.set(true);
	}
	
	public final void resume() throws Exception {
		if(!paused.get()) return;
		doWithProcesses((p) -> ProcessUtils.resume(p.process()));
		paused.set(false);
	}
	
	public final List<ReadOnlyProcess> processes() {
		return Collections.unmodifiableList(processes);
	}
	
	public final boolean isPaused() {
		return paused.get();
	}
	
	public final boolean isStopped() {
		return stopped.get();
	}
}