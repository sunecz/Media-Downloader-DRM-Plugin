package sune.app.mediadown.drm;

import java.util.Locale;

import sune.app.mediadown.Arguments;
import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.StartupWindow;
import sune.app.mediadown.drm.event.DRMBootstrapEvent;
import sune.app.mediadown.plugin.PluginBootstrap;
import sune.app.mediadown.plugin.PluginBootstrapBase;
import sune.app.mediadown.resource.Resources.StringReceiver;
import sune.app.mediadown.util.Reflection2;

@PluginBootstrap(pluginClass=DRMPlugin.class)
public final class DRMPluginBootstrap extends PluginBootstrapBase {
	
	DRMPluginBootstrap() {
	}
	
	private static final String format(String format, Object... args) {
		return String.format(Locale.US, format, args);
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
	}
	
	@Override
	public void init() throws Exception {
		// Use the default string receiver of the Startup window
		Class<?> classStates = Reflection2.getClass("sune.app.mediadown.MediaDownloader$InitializationStates");
		StartupWindow window = Reflection2.getField(classStates, null, "window");
		// Bootstrap the DRM system
		DRMBootstrap.Builder builder = new DRMBootstrap.Builder();
		Arguments arguments = MediaDownloader.arguments();
		
		// Optionally generate hash lists, if required
		builder.generateHashLists(arguments.booleanValue("drm-generate-lists"));
		
		DRMBootstrap bootstrap = builder.build();
		addDefaultBootstrapListeners(bootstrap, window != null ? (text) -> window.setText(text) : (text) -> {});
		bootstrap.run(MediaDownloader.class);
	}
	
	@Override
	public void dispose() throws Exception {
		// Do nothing
	}
}