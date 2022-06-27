package sune.app.mediadownloader.drm.util;

import java.util.Set;
import java.util.stream.Collectors;

import sune.app.mediadown.util.OSUtils;

public final class AudioRedirector {
	
	private static final String DEVICE_NAME_VIRTUAL_CABLE = "VB-Audio Virtual Cable\\Device\\CABLE Input\\Render";
	
	// Forbid anyone to create an instance of this class
	private AudioRedirector() {
	}
	
	public static final void redirectToVirtual() throws Exception {
		redirect(DEVICE_NAME_VIRTUAL_CABLE);
	}
	
	public static final void redirect(String audioDeviceName) throws Exception {
		// Windows-only for now
		if(OSUtils.isWindows()) {
			// Get the PIDs of all subprocesses
			Set<Long> subPIDs = ProcessHandle.current().descendants().map(ProcessHandle::pid).collect(Collectors.toSet());
			// Get the PIDs of all applications that emit sound
			Set<Long> appPIDs = SoundVolumeView.getAudioAppsPIDs();
			// Intersection of both sets yields the exact PID of CEF subprocess that emits the audio we want
			Set<Long> thePIDs = appPIDs.stream().filter(subPIDs::contains).collect(Collectors.toSet());
			if(thePIDs.isEmpty())
				throw new IllegalStateException("Unable to redirect the audio output");
			// Redirect the audio output of the browser
			long pid = thePIDs.iterator().next();
			SoundVolumeView.setAudioOutput(pid, audioDeviceName);
		}
	}
}