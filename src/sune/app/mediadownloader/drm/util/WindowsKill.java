package sune.app.mediadownloader.drm.util;

import java.nio.file.Path;
import java.nio.file.Paths;

import sune.api.process.Processes;
import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.PathSystem;

// Windows Kill Utility (https://github.com/ElyDotDev/windows-kill)
public final class WindowsKill {
	
	private static Path fileBinary;
	private static final Path ensureBinary() {
		if((fileBinary == null)) {
			fileBinary = Paths.get(PathSystem.getFullPath(WindowsKill.class, "resources/binary/drm"), "windows-kill.exe");
			if(!NIO.isRegularFile(fileBinary))
				throw new IllegalStateException("windows-kill.exe was not found at: " + fileBinary.toAbsolutePath().toString());
		}
		return fileBinary;
	}
	
	public static final boolean interrupt(long pid) throws Exception {
		ensureBinary();
		try(ReadOnlyProcess process = Processes.createSynchronous(fileBinary)) {
			process.execute(String.format("-SIGINT %d", pid));
			return process.waitFor() == 0;
		}
	}
	
	// Forbid anyone to create an instance of this class
	private WindowsKill() {
	}
}