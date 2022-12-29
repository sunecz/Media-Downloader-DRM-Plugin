package sune.app.mediadownloader.drm.integration;

import static sune.app.mediadown.gui.window.ConfigurationWindow.isOfEnumClass;
import static sune.app.mediadown.gui.window.ConfigurationWindow.registerFormField;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sune.app.mediadown.configuration.Configuration.ConfigurationProperty;
import sune.app.mediadown.gui.form.field.TranslatableSelectField.ValueTransformer;
import sune.app.mediadown.language.Translation;
import sune.app.mediadown.plugin.PluginConfiguration;
import sune.app.mediadown.plugin.Plugins;
import sune.app.mediadownloader.drm.DRMConfiguration;
import sune.app.mediadownloader.drm.DRMConstants;

public class DRMPluginConfiguration {
	
	private static final String PROPERTY_RECORD_USE_MEDIA_FPS = "record.useMediaFps";
	private static final String PROPERTY_RECORD_DEFAULT_FPS = "record.defaultFps";
	private static final String PROPERTY_PROCESS_KEEP_RECORD_FILE = "process.keepRecordFile";
	private static final String PROPERTY_PROCESS_KEEP_TEMPORARY_FILES = "process.keepTemporaryFiles";
	private static final String PROPERTY_QUALITY = "quality";
	private static final String PROPERTY_DEBUG = "debug";
	
	private static DRMPluginConfiguration instance;
	private static boolean customFieldsRegistered = false;
	
	private final PluginConfiguration configuration;
	
	private boolean record_useMediaFps;
	private double record_defaultFps;
	private boolean process_keepRecordFile;
	private boolean process_keepTemporaryFiles;
	private DRMConfiguration.Quality quality;
	private boolean debug;
	
	private DRMPluginConfiguration(PluginConfiguration configuration) {
		this.configuration = Objects.requireNonNull(configuration);
		this.loadFields();
	}
	
	private static final Translation translationOf(String translationPath) {
		return Plugins.getLoaded("drm").getInstance().translation().getTranslation(translationPath);
	}
	
	private static final <T> Function<T, String> valueTranslator(String translationPath,
			Function<T, String> stringConverter) {
		Objects.requireNonNull(translationPath);
		Objects.requireNonNull(stringConverter);
		
		LazyValue<Translation> tr = new LazyValue<>(() -> translationOf(translationPath));
		return ((v) -> tr.value().getSingle(stringConverter.apply(v)));
	}
	
	private static final <T> Function<T, String> localValueTranslator(String propertyName,
			Function<T, String> stringConverter) {
		Objects.requireNonNull(propertyName);
		return valueTranslator("configuration.values." + propertyName, stringConverter);
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
		builder.addProperty(ConfigurationProperty.ofType(PROPERTY_QUALITY, DRMConfiguration.Quality.class)
			.inGroup(group)
			.withFactory(() -> Stream.of(DRMConfiguration.Quality.values())
			                         .map(Enum::name)
			                         .collect(Collectors.toList()))
			.withTransformer(Enum::name, DRMConfiguration.Quality::of)
			.withDefaultValue(DRMConfiguration.Quality.LOSSLESS.name()));
		builder.addProperty(ConfigurationProperty.ofBoolean(PROPERTY_DEBUG)
			.inGroup(group)
			.withDefaultValue(false));
		
		// <---- Register custom fields
		
		if(!customFieldsRegistered) {
			registerFormField(isOfEnumClass(DRMConfiguration.Quality.class, DRMConfiguration.Quality::validValues,
				ValueTransformer.of(DRMConfiguration.Quality::of, Enum::name, localValueTranslator(
					PROPERTY_QUALITY, Enum::name
				))
			));
			
			customFieldsRegistered = true;
		}
		
		// ---->
		
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
		quality = DRMConfiguration.Quality.of(configuration.stringValue(PROPERTY_QUALITY));
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
	
	public DRMConfiguration.Quality quality() {
		return quality;
	}
	
	public boolean debug() {
		return debug;
	}
	
	public PluginConfiguration configuration() {
		return configuration;
	}
	
	private static final class LazyValue<T> {
		
		private final Supplier<T> supplier;
		private T value;
		private boolean initialized;
		
		public LazyValue(Supplier<T> supplier) {
			this.supplier = Objects.requireNonNull(supplier);
		}
		
		public T value() {
			if(!initialized) {
				value = supplier.get();
				initialized = true;
			}
			
			return value;
		}
	}
}