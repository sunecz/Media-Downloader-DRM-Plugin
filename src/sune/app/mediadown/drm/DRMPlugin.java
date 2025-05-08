package sune.app.mediadown.drm;

import java.util.logging.Level;

import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.configuration.Configuration.ConfigurationProperty;
import sune.app.mediadown.drm.util.Common;
import sune.app.mediadown.plugin.Plugin;
import sune.app.mediadown.plugin.PluginBase;
import sune.app.mediadown.plugin.PluginConfiguration;
import sune.app.mediadown.transformer.Transformers;

@Plugin(name          = "drm",
		title         = "plugin.drm.title",
		version       = "00.02.09-0022",
		author        = "Sune",
		updateBaseURL = "https://app.sune.tech/mediadown/dat/plugin/0002/plugin/drm/",
		updatable     = true,
		url           = "https://projects.suneweb.net/media-downloader/",
		icon          = "resources/drm/icon/drm.png",
		moduleName    = "sune.app.mediadown.drm")
public final class DRMPlugin extends PluginBase {
	
	private static final String NAME = "drm";
	
	// Default values of configuration properties
	private static final int DEFAULT_KEYS_MAX_RETRY_ATTEMPTS = 5;
	private static final int DEFAULT_WAIT_ON_RETRY_MS = 250;
	private static final boolean DEFAULT_ENABLE_LOGGING = false;
	
	private String translatedTitle;
	private PluginConfiguration.Builder configuration;
	
	private final void initConfiguration() {
		PluginConfiguration.Builder builder
			= new PluginConfiguration.Builder(getContext().getPlugin().instance().name());
		String group = builder.name() + ".general";
		
		builder.addProperty(ConfigurationProperty.ofInteger("keysMaxRetryAttempts")
			.inGroup(group)
			.withDefaultValue(DEFAULT_KEYS_MAX_RETRY_ATTEMPTS)
			.withOrder(60));
		builder.addProperty(ConfigurationProperty.ofInteger("waitOnRetryMs")
			.inGroup(group)
			.withDefaultValue(DEFAULT_WAIT_ON_RETRY_MS)
			.withOrder(80));
		builder.addProperty(ConfigurationProperty.ofBoolean("enableLogging")
			.inGroup(group)
			.withDefaultValue(DEFAULT_ENABLE_LOGGING)
			.withOrder(100));
		
		configuration = builder;
	}
	
	@Override
	public void init() throws Exception {
		translatedTitle = MediaDownloader.translation().getSingle(super.getTitle());
		Transformers.add(NAME, DecryptionTransformer.class);
		initConfiguration();
	}
	
	@Override
	public void dispose() throws Exception {
		// Do nothing
	}
	
	@Override
	public void afterBuildConfiguration() throws Exception {
		PluginConfiguration configuration = getContext().getConfiguration();
		boolean loggingEnabled = configuration.booleanValue("enableLogging");
		Common.initialize(loggingEnabled ? Level.ALL : Level.OFF);
	}
	
	@Override
	public PluginConfiguration.Builder configuration() {
		return configuration;
	}
	
	@Override
	public String getTitle() {
		return translatedTitle;
	}
}