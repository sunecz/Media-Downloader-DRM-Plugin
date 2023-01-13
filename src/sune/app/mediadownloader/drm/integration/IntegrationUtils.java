package sune.app.mediadownloader.drm.integration;

import java.io.InputStream;
import java.nio.file.Path;

import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.language.Translation;
import sune.app.mediadown.plugin.PluginFile;
import sune.app.mediadown.plugin.PluginResource;
import sune.app.mediadown.util.PathSystem;
import sune.app.mediadown.util.Utils.Ignore;

public final class IntegrationUtils {
	
	private static PluginFile pluginContext;
	
	// Forbid anyone to create an instance of this class
	private IntegrationUtils() {
	}
	
	public static void setPluginContext(PluginFile context) {
		pluginContext = context;
	}
	
	public static final InputStream resourceStream(String path) {
		return pluginContext != null
					? PluginResource.stream(pluginContext, path.startsWith("/") ? path.substring(1) : path)
					: IntegrationUtils.class.getResourceAsStream(path);
	}
	
	public static final Translation translation() {
		return MediaDownloader.translation().getTranslation("plugin.drm");
	}
	
	public static final boolean runInJAR() {
		return MediaDownloader.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm().endsWith(".jar");
	}
	
	public static final Path jarPath() {
		Path path = Ignore.call(() -> Path.of(MediaDownloader.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
		// Special case for running from a build directory
		if(path != null && !path.getFileName().toString().endsWith(".jar")) {
			path = path.getParent().resolve("jar/media-downloader.jar");
		}
		return path.toAbsolutePath();
	}
	
	public static final Path currentDirectory() {
		return PathSystem.getPath(MediaDownloader.class, "");
	}
}