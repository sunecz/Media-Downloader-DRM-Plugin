package sune.app.mediadownloader.drm.util;

import java.nio.file.Path;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.ffmpeg.FFProbe;
import sune.app.mediadown.util.Utils;

public final class DRMProcessUtils {
	
	// Forbid anyone to create an instance of this class
	private DRMProcessUtils() {
	}
	
	public static final double duration(Path path) throws Exception {
		try(ReadOnlyProcess process = FFProbe.createSynchronousProcess()) {
			String command = Utils.format(
				" -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \"%{path}s\"",
				"path", path.toString());
			return Double.parseDouble(process.execute(command));
		} catch(NumberFormatException ex) {
			return Double.NaN;
		}
	}
	
	public static final void throwIfFailedFFMpeg(int code) {
		throwIfFailedFFMpeg(code, 0);
	}
	
	public static final void throwIfFailedFFMpeg(int code, int requiredCode) {
		if(code != requiredCode) {
			throw new IllegalStateException("FFMpeg ended with code " + code);
		}
	}
}