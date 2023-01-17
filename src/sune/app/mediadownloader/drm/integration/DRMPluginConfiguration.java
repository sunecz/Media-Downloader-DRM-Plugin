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
import sune.app.mediadownloader.drm.DRMConstants;
import sune.app.mediadownloader.drm.util.Quality;

public class DRMPluginConfiguration {
	
	private static final String PROPERTY_RECORD_USE_DISPLAY_REFRESH_RATE = "record.useDisplayRefreshRate";
	private static final String PROPERTY_RECORD_DEFAULT_FRAME_RATE = "record.defaultFrameRate";
	private static final String PROPERTY_OUTPUT_USE_MEDIA_FRAME_RATE = "output.useMediaFrameRate";
	private static final String PROPERTY_OUTPUT_DEFAULT_FRAME_RATE = "output.defaultFrameRate";
	private static final String PROPERTY_PROCESS_KEEP_RECORD_FILE = "process.keepRecordFile";
	private static final String PROPERTY_QUALITY = "quality";
	private static final String PROPERTY_DEBUG = "debug";
	
	private static DRMPluginConfiguration instance;
	private static boolean customFieldsRegistered = false;
	
	private final PluginConfiguration configuration;
	
	private boolean record_useDisplayRefreshRate;
	private double record_defaultFrameRate;
	private boolean output_useMediaFrameRate;
	private double output_defaultFrameRate;
	private boolean process_keepRecordFile;
	private Quality quality;
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
		
		builder.addProperty(ConfigurationProperty.ofBoolean(PROPERTY_RECORD_USE_DISPLAY_REFRESH_RATE)
			.inGroup(group)
			.withDefaultValue(true));
		builder.addProperty(ConfigurationProperty.ofDecimal(PROPERTY_RECORD_DEFAULT_FRAME_RATE)
			.inGroup(group)
			.withDefaultValue(DRMConstants.DEFAULT_FRAMERATE));
		builder.addProperty(ConfigurationProperty.ofBoolean(PROPERTY_OUTPUT_USE_MEDIA_FRAME_RATE)
			.inGroup(group)
			.withDefaultValue(true));
		builder.addProperty(ConfigurationProperty.ofDecimal(PROPERTY_OUTPUT_DEFAULT_FRAME_RATE)
			.inGroup(group)
			.withDefaultValue(DRMConstants.DEFAULT_FRAMERATE));
		builder.addProperty(ConfigurationProperty.ofBoolean(PROPERTY_PROCESS_KEEP_RECORD_FILE)
			.inGroup(group)
			.withDefaultValue(false));
		builder.addProperty(ConfigurationProperty.ofType(PROPERTY_QUALITY, Quality.class)
			.inGroup(group)
			.withFactory(() -> Stream.of(Quality.values())
			                         .map(Enum::name)
			                         .collect(Collectors.toList()))
			.withTransformer(Enum::name, Quality::of)
			.withDefaultValue(Quality.LOSSLESS.name()));
		builder.addProperty(ConfigurationProperty.ofBoolean(PROPERTY_DEBUG)
			.inGroup(group)
			.withDefaultValue(false));
		
		// <---- Register custom fields
		
		if(!customFieldsRegistered) {
			registerFormField(isOfEnumClass(Quality.class, Quality::validValues,
				ValueTransformer.of(Quality::of, Enum::name, localValueTranslator(
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
		record_useDisplayRefreshRate = configuration.booleanValue(PROPERTY_RECORD_USE_DISPLAY_REFRESH_RATE);
		record_defaultFrameRate = configuration.doubleValue(PROPERTY_RECORD_DEFAULT_FRAME_RATE);
		output_useMediaFrameRate = configuration.booleanValue(PROPERTY_OUTPUT_USE_MEDIA_FRAME_RATE);
		output_defaultFrameRate = configuration.doubleValue(PROPERTY_OUTPUT_DEFAULT_FRAME_RATE);
		process_keepRecordFile = configuration.booleanValue(PROPERTY_PROCESS_KEEP_RECORD_FILE);
		quality = Quality.of(configuration.stringValue(PROPERTY_QUALITY));
		debug = configuration.booleanValue(PROPERTY_DEBUG);
	}
	
	public boolean recordUseDisplayRefreshRate() {
		return record_useDisplayRefreshRate;
	}
	
	public double recordDefaultFrameRate() {
		return record_defaultFrameRate;
	}
	
	public boolean outputUseMediaFrameRate() {
		return output_useMediaFrameRate;
	}
	
	public double outputDefaultFrameRate() {
		return output_defaultFrameRate;
	}
	
	public boolean processKeepRecordFile() {
		return process_keepRecordFile;
	}
	
	public Quality quality() {
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