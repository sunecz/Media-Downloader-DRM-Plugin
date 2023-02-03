package sune.app.mediadownloader.drm.integration;

import static sune.app.mediadown.gui.window.ConfigurationWindow.isOfEnumClass;
import static sune.app.mediadown.gui.window.ConfigurationWindow.registerFormField;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.configuration.Configuration.ConfigurationProperty;
import sune.app.mediadown.gui.ProgressWindow;
import sune.app.mediadown.gui.ProgressWindow.ProgressAction;
import sune.app.mediadown.gui.ProgressWindow.ProgressContext;
import sune.app.mediadown.gui.form.Form;
import sune.app.mediadown.gui.form.FormField;
import sune.app.mediadown.gui.form.field.TranslatableSelectField.ValueTransformer;
import sune.app.mediadown.gui.window.ConfigurationWindow;
import sune.app.mediadown.gui.window.ConfigurationWindow.ConfigurationFormFieldProperty;
import sune.app.mediadown.gui.window.ConfigurationWindow.FormFieldSupplier;
import sune.app.mediadown.gui.window.ConfigurationWindow.FormFieldSupplierFactory;
import sune.app.mediadown.language.Translation;
import sune.app.mediadown.plugin.PluginConfiguration;
import sune.app.mediadown.plugin.Plugins;
import sune.app.mediadown.util.FXUtils;
import sune.app.mediadown.util.Utils;
import sune.app.mediadown.util.Utils.Ignore;
import sune.app.mediadownloader.drm.DRMConstants;
import sune.app.mediadownloader.drm.util.AudioDevices;
import sune.app.mediadownloader.drm.util.AudioDevices.AudioDevice;
import sune.app.mediadownloader.drm.util.AudioDevices.AudioDevice.Direction;
import sune.app.mediadownloader.drm.util.Quality;
import sune.util.ssdf2.SSDType;
import sune.util.ssdf2.SSDValue;

public class DRMPluginConfiguration {
	
	private static final String PROPERTY_RECORD_USE_DISPLAY_REFRESH_RATE = "record.useDisplayRefreshRate";
	private static final String PROPERTY_RECORD_DEFAULT_FRAME_RATE = "record.defaultFrameRate";
	private static final String PROPERTY_OUTPUT_USE_MEDIA_FRAME_RATE = "output.useMediaFrameRate";
	private static final String PROPERTY_OUTPUT_DEFAULT_FRAME_RATE = "output.defaultFrameRate";
	private static final String PROPERTY_PROCESS_KEEP_RECORD_FILE = "process.keepRecordFile";
	private static final String PROPERTY_QUALITY = "quality";
	private static final String PROPERTY_AUDIO_CAPTURE_DEVICE_NAME = "audio.captureDeviceName";
	private static final String PROPERTY_AUDIO_RENDER_DEVICE_NAME = "audio.renderDeviceName";
	private static final String PROPERTY_AUDIO_ALLOW_VIRTUAL_DEVICE = "audio.allowVirtualDevice";
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
	private String audio_captureDeviceName;
	private String audio_renderDeviceName;
	private boolean audio_allowVirtualDevice;
	private boolean debug;
	
	private DRMPluginConfiguration(PluginConfiguration configuration) {
		this.configuration = Objects.requireNonNull(configuration);
		this.loadFields();
	}
	
	private static final String fullPropertyName(String propertyName) {
		return "drm." + propertyName;
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
	
	private static final String fixAudioDeviceName(String audioDeviceName) {
		return audioDeviceName != null ? audioDeviceName.replaceAll("/", "\\\\") : "auto";
	}
	
	protected static final PluginConfiguration.Builder builder(String configurationName) {
		PluginConfiguration.Builder builder = new PluginConfiguration.Builder(configurationName);
		String group = builder.name() + ".general";
		
		builder.addProperty(ConfigurationProperty.ofString(PROPERTY_AUDIO_CAPTURE_DEVICE_NAME)
			.inGroup(group)
			.withDefaultValue("auto"));
		builder.addProperty(ConfigurationProperty.ofString(PROPERTY_AUDIO_RENDER_DEVICE_NAME)
			.inGroup(group)
			.withDefaultValue("auto"));
		builder.addProperty(ConfigurationProperty.ofBoolean(PROPERTY_AUDIO_ALLOW_VIRTUAL_DEVICE)
			.inGroup(group)
			.withDefaultValue(false));
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
			
			registerFormField(AudioDeviceFormFieldSupplierFactory.of(
				fullPropertyName(PROPERTY_AUDIO_CAPTURE_DEVICE_NAME), Direction.CAPTURE
			));
			
			registerFormField(AudioDeviceFormFieldSupplierFactory.of(
				fullPropertyName(PROPERTY_AUDIO_RENDER_DEVICE_NAME), Direction.RENDER
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
		audio_captureDeviceName = fixAudioDeviceName(configuration.stringValue(PROPERTY_AUDIO_CAPTURE_DEVICE_NAME));
		audio_renderDeviceName = fixAudioDeviceName(configuration.stringValue(PROPERTY_AUDIO_RENDER_DEVICE_NAME));
		audio_allowVirtualDevice = configuration.booleanValue(PROPERTY_AUDIO_ALLOW_VIRTUAL_DEVICE);
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
	
	public String audioCaptureDeviceName() {
		return audio_captureDeviceName;
	}
	
	public String audioRenderDeviceName() {
		return audio_renderDeviceName;
	}
	
	public boolean audioAllowVirtualDevice() {
		return audio_allowVirtualDevice;
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
	
	private static final class AudioDeviceFormFieldSupplierFactory implements FormFieldSupplierFactory {
		
		private static Translation translation;
		
		private final String propertyName;
		private final Direction direction;
		
		private AudioDeviceFormFieldSupplierFactory(String propertyName, Direction direction) {
			this.propertyName = propertyName;
			this.direction = direction;
		}
		
		private static final Translation translation() {
			if(translation == null) {
				translation = translationOf("configuration.values.audio");
			}
			
			return translation;
		}
		
		private static final String saveAudioDevice(String audioDeviceAlternativeName) {
			return audioDeviceAlternativeName.replaceAll("\\\\", "/");
		}
		
		private static final String loadAudioDevice(String string) {
			return Utils.removeStringQuotes(string).replaceAll("/", "\\\\");
		}
		
		public static final AudioDeviceFormFieldSupplierFactory of(String propertyName, Direction direction) {
			return new AudioDeviceFormFieldSupplierFactory(propertyName, direction);
		}
		
		private final List<AudioDevice> audioDevices() throws Exception {
			switch(direction) {
				case CAPTURE:
					return AudioDevices.captureAudioDevices();
				case RENDER:
					return AudioDevices.renderAudioDevices();
				default:
					throw new IllegalStateException("Unsupported audio direction: " + direction);
			}
		}
		
		private final AudioDevice automaticAudioDevice() {
			switch(direction) {
				case CAPTURE:
				case RENDER:
					return AudioDevices.newDevice(translation().getSingle("device_auto"), "auto", direction);
				default:
					throw new IllegalStateException("Unsupported audio direction: " + direction);
			}
		}
		
		@Override
		public FormFieldSupplier create(String name, ConfigurationFormFieldProperty fieldProperty) {
			return name.equals(propertyName) ? AudioDeviceSelectField::new : null;
		}
		
		private final class AudioDeviceSelectField<T> extends FormField<T> {
			
			private volatile boolean itemsLoaded = false;
			
			private final ComboBox<AudioDevice> control;
			private String loadedValue;
			
			public AudioDeviceSelectField(T property, String name, String title) {
				super(property, name, title);
				control = new ComboBox<>();
				control.setCellFactory((p) -> new AudioDeviceCell());
				control.setButtonCell(new AudioDeviceCell());
				control.setMaxWidth(Double.MAX_VALUE);
				
				FXUtils.onWindowShow(control, () -> {
					ConfigurationWindow window = (ConfigurationWindow) control.getScene().getWindow();
					TabPane tabPane = (TabPane) window.getContent().getCenter();
					
					String tabTitle = translationOf("configuration.group").getSingle("general");
					Tab tab = tabPane.getTabs().stream()
						.filter((t) -> t.getText().equals(tabTitle))
						.findFirst().orElse(null);
					
					if(tab != null) {
						FXUtils.once(tab.selectedProperty(), (so, sov, snv) -> {
							if(!snv) return; // Should not happen
							loadItems();
						});
					}
				});
			}
			
			private final void loadItems() {
				Stage parent = (Stage) control.getScene().getWindow();
				
				ProgressWindow.submitAction(parent, new ProgressAction() {
					
					@Override
					public void action(ProgressContext context) {
						context.setProgress(ProgressContext.PROGRESS_INDETERMINATE);
						context.setText(translation().getSingle(
							"progress.devices_" + direction.name().toLowerCase()
						));
						
						Ignore.callVoid(() -> {
							List<AudioDevice> audioDevices = audioDevices();
							audioDevices.add(0, automaticAudioDevice());
							
							AudioDevice selected = audioDevices.stream()
								.filter((d) -> d.alternativeName().equals(loadedValue))
								.findFirst().orElse(null);
							
							FXUtils.thread(() -> {
								control.getItems().setAll(audioDevices);
								
								if(selected != null) {
									control.getSelectionModel().select(selected);
								}
							});
						}, MediaDownloader::error);
						
						itemsLoaded = true;
						context.setProgress(ProgressContext.PROGRESS_DONE);
					}
					
					@Override
					public void cancel() {
						// Currently not cancelable
					}
				});
			}
			
			@Override
			public Node render(Form form) {
				return control;
			}
			
			@Override
			public void value(SSDValue value, SSDType type) {
				loadedValue = loadAudioDevice(value.stringValue());
			}
			
			@Override
			public Object value() {
				return saveAudioDevice(
					itemsLoaded
						? control.getSelectionModel().getSelectedItem().alternativeName()
						: loadedValue
				);
			}
		}
		
		private static final class AudioDeviceCell extends ListCell<AudioDevice> {
			
			@Override
			protected void updateItem(AudioDevice item, boolean empty) {
				super.updateItem(item, empty);
				
				if(!empty) {
					setText(item.name());
				} else {
					setText(null);
					setGraphic(null);
				}
			}
		}
	}
}