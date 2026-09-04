package sune.app.mediadown.drm.util;

import java.nio.file.Path;
import java.util.function.Consumer;

import sune.api.process.Processes;
import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.os.OS;
import sune.app.mediadown.util.NIO;

public final class MP4Decrypt {
	
	private static Path path;
	
	// Forbid anyone to create an instance of this class
	private MP4Decrypt() {
	}
	
	private static final Path ensureBinary() {
		if(path == null) {
			path = NIO.localPath("resources/binary/drm", OS.current().executableFileName("mp4decrypt"));
			
			if(!NIO.isRegularFile(path)) {
				throw new IllegalStateException("mp4decrypt utility was not found at " + path.toAbsolutePath().toString());
			}
		}
		
		return path;
	}
	
	public static final Path path() {
		return ensureBinary();
	}
	
	public static final ReadOnlyProcess createSynchronousProcess() {
		return Processes.createSynchronous(path());
	}
	
	public static final ReadOnlyProcess createAsynchronousProcess(Consumer<String> listener) {
		return Processes.createAsynchronous(path(), listener);
	}
}