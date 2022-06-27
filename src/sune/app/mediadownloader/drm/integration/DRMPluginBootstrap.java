package sune.app.mediadownloader.drm.integration;

import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.StartupWindow;
import sune.app.mediadown.configuration.Configuration.ConfigurationProperty;
import sune.app.mediadown.plugin.PluginBootstrap;
import sune.app.mediadown.plugin.PluginBootstrapBase;
import sune.app.mediadown.plugin.PluginConfiguration;
import sune.app.mediadown.plugin.PluginConfiguration.Builder;
import sune.app.mediadown.resource.Resources.StringReceiver;
import sune.app.mediadown.util.PathSystem;
import sune.app.mediadown.util.Reflection2;
import sune.app.mediadownloader.drm.DRMBootstrap;
import sune.app.mediadownloader.drm.event.DRMBootstrapEvent;
import sune.util.load.Libraries.Library;

@PluginBootstrap(pluginClass=DRMPlugin.class)
public final class DRMPluginBootstrap extends PluginBootstrapBase {
	
	private PluginConfiguration.Builder configuration;
	
	private final void addDefaultBootstrapListeners(DRMBootstrap bootstrap, StringReceiver receiver) {
		bootstrap.addEventListener(DRMBootstrapEvent.RESOURCE_DOWNLOAD_BEGIN, (context) -> {
			receiver.receive(String.format("Download: %s -> %s", context.url().toExternalForm(),
			                               context.path().toAbsolutePath().toString()));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.RESOURCE_DOWNLOAD_UPDATE, (context) -> {
			receiver.receive(String.format("Downloading %s... %.2f%%", context.path().getFileName().toString(),
			                               context.tracker().getProgress() * 100.0));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.RESOURCE_DOWNLOAD_BEGIN, (context) -> {
			receiver.receive(String.format("Download done"));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.RESOURCE_CHECK, (context) -> {
			receiver.receive(String.format("Checking %s", context.name()));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.LIBRARY_LOADING, (context) -> {
			receiver.receive(String.format("Loading library %s...", context.library().getName()));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.LIBRARY_LOADED, (context) -> {
			receiver.receive(String.format("Loading library %s... %s", context.library().getName(),
			                               context.success() ? "done" : "error"));
		});
		bootstrap.addEventListener(DRMBootstrapEvent.LIBRARIES_ERROR, (context) -> {
			String text = String.format("Cannot load libraries (%d)", context.libraries().length);
			StringBuilder content = new StringBuilder();
			for(Library library : context.libraries()) {
				content.append(String.format("%s (%s)\n", library.getName(), library.getPath()));
			}
			String errorText = String.format("Critical error: %s\n\n%s\n", text, content.toString());
			context.context().error(new IllegalStateException(errorText));
		});
	}
	
	private final void initConfiguration() {
		PluginConfiguration.Builder builder = new PluginConfiguration.Builder(context().getPlugin().instance().name());
		builder.addProperty(ConfigurationProperty.ofBoolean("debug").withDefaultValue(false));
		configuration = builder;
	}
	
	@Override
	public void init() throws Exception {
		// Use the default string receiver of the Startup window
		Class<?> classStates = Reflection2.getClass("sune.app.mediadown.MediaDownloader$InitializationStates");
		StartupWindow window = Reflection2.getField(classStates, null, "window");
		// Bootstrap the DRM system
		DRMBootstrap.Builder builder = new DRMBootstrap.Builder();
		
		// Obtain the configuration and its values
		PluginConfiguration config = context().getConfiguration();
		boolean debugMode = config.booleanValue("debug");
		
		// Enable the Debug mode based on the configuration
		if(debugMode) {
			builder.debug(true);
			builder.logFile(PathSystem.getPath(MediaDownloader.class, "drm-debug.log"));
		}
		
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