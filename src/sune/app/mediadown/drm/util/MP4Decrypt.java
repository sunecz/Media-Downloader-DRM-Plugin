package sune.app.mediadown.drm.util;

import java.nio.file.Path;
import java.util.function.Consumer;

import sune.api.process.Processes;
import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.OSUtils;
import sune.app.mediadown.util.Utils;

public final class MP4Decrypt {
	
	private static Path path;
	
	// Forbid anyone to create an instance of this class
	private MP4Decrypt() {
	}
	
	private static final Path ensureBinary() {
		if(path == null) {
			path = NIO.localPath("resources/binary/drm", OSUtils.getExecutableName("mp4decrypt"));
			
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
	
	public static final int decrypt(Path input, Path output, MediaDecryptionKey key) throws Exception {
		try(ReadOnlyProcess process = createAsynchronousProcess((l) -> {})) {
			String command = Utils.format(
				"--key %{kid}s:%{key}s \"%{input}s\" \"%{output}s\"",
				"kid", key.kid(),
				"key", key.key(),
				"input", input.toAbsolutePath().toString(),
				"output", output.toAbsolutePath().toString()
			);
			
			process.execute(command);
			return process.waitFor();
		}
	}
}