package sune.app.mediadownloader.drm.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.StartupWindow;
import sune.app.mediadown.plugin.PluginBootstrap;
import sune.app.mediadown.plugin.PluginBootstrapBase;
import sune.app.mediadown.plugin.PluginConfiguration;
import sune.app.mediadown.plugin.PluginConfiguration.Builder;
import sune.app.mediadown.resource.Resources.StringReceiver;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.Reflection2;
import sune.app.mediadown.util.Utils;
import sune.app.mediadown.util.Utils.Ignore;
import sune.app.mediadown.util.Utils.OfPath.Info;
import sune.app.mediadownloader.drm.DRMBootstrap;
import sune.app.mediadownloader.drm.event.DRMBootstrapEvent;
import sune.util.load.Libraries.Library;

@PluginBootstrap(pluginClass=DRMPlugin.class)
public final class DRMPluginBootstrap extends PluginBootstrapBase {
	
	private static final Path LOG_DIRECTORY = NIO.localPath("resources/log");
	private static final Path LOG_PATH = LOG_DIRECTORY.resolve("drm.log");
	
	private static DateTimeFormatter dateTimeFormatter;
	
	private PluginConfiguration.Builder configuration;
	
	private static final String format(String format, Object... args) {
		return String.format(Locale.US, format, args);
	}
	
	private static final DateTimeFormatter dateTimeFormatter() {
		return dateTimeFormatter == null
					? dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneId.of("UTC"))
					: dateTimeFormatter;
	}
	
	private static final String format(Instant instant) {
		return dateTimeFormatter().format(instant);
	}
	
	private final void addDefaultBootstrapListeners(DRMBootstrap bootstrap, StringReceiver receiver) {
		bootstrap.addEventListener(DRMBootstrapEvent.RESOURCE_DOWNLOAD_BEGIN, (context) -> {
			receiver.receive(format("Download: %s -> %s", context.uri().toString(),
			                        context.path().toAbsolutePath().toString()));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.RESOURCE_DOWNLOAD_UPDATE, (context) -> {
			receiver.receive(format("Downloading %s... %.2f%%", context.path().getFileName().toString(),
			                        context.tracker().progress() * 100.0));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.RESOURCE_DOWNLOAD_BEGIN, (context) -> {
			receiver.receive(format("Download done"));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.RESOURCE_CHECK, (context) -> {
			receiver.receive(format("Checking %s", context.name()));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.LIBRARY_LOADING, (context) -> {
			receiver.receive(format("Loading library %s...", context.library().getName()));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.LIBRARY_LOADED, (context) -> {
			receiver.receive(format("Loading library %s... %s", context.library().getName(),
			                        context.success() ? "done" : "error"));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.LIBRARIES_ERROR, (context) -> {
			String text = format("Cannot load libraries (%d)", context.libraries().length);
			StringBuilder content = new StringBuilder();
			for(Library library : context.libraries()) {
				content.append(format("%s (%s)\n", library.getName(), library.getPath()));
			}
			String errorText = format("Critical error: %s\n\n%s\n", text, content.toString());
			context.context().error(new IllegalStateException(errorText));
		});
	}
	
	private final void initConfiguration() {
		configuration = DRMPluginConfiguration.builder(context().getPlugin().instance().name());
	}
	
	@Override
	public void init() throws Exception {
		// Use the default string receiver of the Startup window
		Class<?> classStates = Reflection2.getClass("sune.app.mediadown.MediaDownloader$InitializationStates");
		StartupWindow window = Reflection2.getField(classStates, null, "window");
		// Bootstrap the DRM system
		DRMBootstrap.Builder builder = new DRMBootstrap.Builder();
		
		// Obtain the configuration and its values
		DRMPluginConfiguration configuration = DRMPluginConfiguration.initialize(context().getConfiguration());
		boolean debugMode = configuration.debug();
		Path pathLogFile = LOG_PATH;
		
		// Backup existing log file, if it already exists and is not empty
		if(NIO.exists(pathLogFile) && NIO.size(pathLogFile) > 0L) {
			Info pathInfo = Utils.OfPath.info(pathLogFile);
			Path newPath = null;
			
			// Try to use the last modified time in the new file name
			newPath = Ignore.call(() -> pathLogFile.resolveSibling(
				pathInfo.fileName() + '_' +
				format(Files.getLastModifiedTime(pathLogFile).toInstant()) +
				'.' + pathInfo.extension()
			));
			
			// If the last modified time cannot be used, use a simple counter
			if(newPath == null) {
				Pattern regexNameWithCounter = Pattern.compile("^" + Pattern.quote(pathInfo.fileName()) + "_(\\d+)\\.log$");
				BiPredicate<Path, BasicFileAttributes> endsWithLog = ((p, a) -> p.getFileName().toString().endsWith(".log"));
				
				int lastCounter = Files.find(pathLogFile.getParent(), 1, endsWithLog)
					.map((path) -> {
						Matcher matcher = regexNameWithCounter.matcher(path.getFileName().toString());
						return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
					})
					.filter(Objects::nonNull)
					.mapToInt(Integer::intValue)
					.max().orElse(0);
				
				int nextCounter = lastCounter + 1;
				newPath = pathLogFile.resolveSibling(pathInfo.fileName() + '_' + nextCounter + ".log");
			}
			
			if(newPath == null) {
				throw new IllegalStateException("Log backup path cannot be null");
			}
			
			// Finally, rename the file to the new name (i.e. backup the old log file)
			NIO.move(pathLogFile, newPath);
		}
		
		// Always specify the log file
		builder.logFile(pathLogFile);
		
		// Enable the Debug mode based on the configuration
		builder.debug(debugMode);
		
		// Optionally generate hash lists, if required
		builder.generateHashLists(MediaDownloader.arguments().booleanValue("drm-generate-lists"));
		
		DRMBootstrap bootstrap = builder.build();
		addDefaultBootstrapListeners(bootstrap, window != null ? (text) -> window.setText(text) : (text) -> {});
		bootstrap.run(MediaDownloader.class);
	}
	
	@Override
	public void dispose() throws Exception {
		// Do nothing
	}
	
	@Override
	public Builder configuration() {
		// Configuration initialization must be done here, since the init() method is called later.
		// This allows the configuration to be used in the init() method.
		if(configuration == null) {
			initConfiguration();
		}
		
		return configuration;
	}
}