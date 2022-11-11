package sune.app.mediadownloader.drm.integration;

import java.util.Objects;

import sune.app.mediadown.configuration.Configuration.ConfigurationProperty;
import sune.app.mediadown.plugin.PluginConfiguration;
import sune.app.mediadownloader.drm.DRMConstants;

public class DRMPluginConfiguration {
	
	private static final String PROPERTY_RECORD_USE_MEDIA_FPS = "record.useMediaFps";
	private static final String PROPERTY_RECORD_DEFAULT_FPS = "record.defaultFps";
	private static final String PROPERTY_PROCESS_KEEP_RECORD_FILE = "process.keepRecordFile";
	private static final String PROPERTY_PROCESS_KEEP_TEMPORARY_FILES = "process.keepTemporaryFiles";
	private static final String PROPERTY_DEBUG = "debug";
	
	private static DRMPluginConfiguration instance;
	
	private final PluginConfiguration configuration;
	
	private boolean record_useMediaFps;
	private double record_defaultFps;
	private boolean process_keepRecordFile;
	private boolean process_keepTemporaryFiles;
	private boolean debug;
	
	private DRMPluginConfiguration(PluginConfiguration configuration) {
		this.configuration = Objects.requireNonNull(configuration);
		this.loadFields();
	}
	
	protected static final PluginConfiguration.Builder builder(String configurationName) {
		PluginConfiguration.Builder builder = new PluginConfiguration.Builder(configurationName);
		String group = builder.name() + ".general";
		
		builder.addProperty(ConfigurationProperty.ofBoolean(PROPERTY_RECORD_USE_MEDIA_FPS)
			.inGroup(group)
			.withDefaultValue(true));
		builder.addProperty(ConfigurationProperty.ofDecimal(PROPERTY_RECORD_DEFAULT_FPS)
			.inGroup(group)
			.withDefaultValue(DRMConstants.DEFAULT_FRAMERATE));
		builder.addProperty(ConfigurationProperty.ofBoolean(PROPERTY_PROCESS_KEEP_RECORD_FILE)
			.inGroup(group)
			.withDefaultValue(false));
		builder.addProperty(ConfigurationProperty.ofBoolean(PROPERTY_PROCESS_KEEP_TEMPORARY_FILES)
			.inGroup(group)
			.withDefaultValue(false));
		builder.addProperty(ConfigurationProperty.ofBoolean(PROPERTY_DEBUG)
			.inGroup(group)
			.withDefaultValue(false));
		
		return builder;
	}
	
	protected static final DRMPluginConfiguration initialize(PluginConfiguration configuration) {
		instance = new DRMPluginConfiguration(configuration);
		return instance;
	}
	
	public static final DRMPluginConfiguration instance() {
		return instance;
	}
	
	private final void loadFields() {
		record_useMediaFps = configuration.booleanValue(PROPERTY_RECORD_USE_MEDIA_FPS);
		record_defaultFps = configuration.doubleValue(PROPERTY_RECORD_DEFAULT_FPS);
		process_keepRecordFile = configuration.booleanValue(PROPERTY_PROCESS_KEEP_RECORD_FILE);
		process_keepTemporaryFiles = configuration.booleanValue(PROPERTY_PROCESS_KEEP_TEMPORARY_FILES);
		debug = configuration.booleanValue(PROPERTY_DEBUG);
	}
	
	public boolean recordUseMediaFps() {
		return record_useMediaFps;
	}
	
	public double recordDefaultFps() {
		return record_defaultFps;
	}
	
	public boolean processKeepRecordFile() {
		return process_keepRecordFile;
	}
	
	public boolean processKeepTemporaryFiles() {
		return process_keepTemporaryFiles;
	}
	
	public boolean debug() {
		return debug;
	}
	
	public PluginConfiguration configuration() {
		return configuration;
	}
}