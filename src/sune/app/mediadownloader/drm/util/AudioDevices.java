package sune.app.mediadownloader.drm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.ffmpeg.FFMpeg;

public final class AudioDevices {
	
	private static List<AudioDevice> dshowDevices;
	
	// Forbid anyone to create an instance of this class
	private AudioDevices() {
	}
	
	private static final boolean isVirtualDevice(AudioDevice.Builder audioDevice) {
		String lowerCaseName = audioDevice.name().toLowerCase();
		return lowerCaseName.contains("cable output") || lowerCaseName.contains("virtual");
	}
	
	public static final List<AudioDevice> directShowDevices() throws Exception {
		if(dshowDevices == null) {
			DirectShowAudioDevicesParser parser = new DirectShowAudioDevicesParser();
			try(ReadOnlyProcess process = FFMpeg.createAsynchronousProcess(parser)) {
				String command = "-f dshow -list_devices true -i dummy -hide_banner";
				process.execute(command);
				DRMProcessUtils.throwIfFailedFFMpeg(process.waitFor(), 1);
			}
			dshowDevices = parser.devices();
		}
		return dshowDevices;
	}
	
	public static final AudioDevice stereoMixDevice() throws Exception {
		return directShowDevices().stream()
					.filter((d) -> d.name().contains("stereo"))
					.findFirst().orElse(null);
	}
	
	public static final AudioDevice virtualDevice() throws Exception {
		return directShowDevices().stream()
					.filter(AudioDevice::isVirtual)
					.findFirst().orElse(null);
	}
	
	public static final class AudioDevice {
		
		private final String name;
		private final String alternativeName;
		private final boolean isVirtual;
		
		private AudioDevice(String name, String alternativeName, boolean isVirtual) {
			this.name = Objects.requireNonNull(name);
			this.alternativeName = Objects.requireNonNull(alternativeName);
			this.isVirtual = isVirtual;
		}
		
		public String name() {
			return name;
		}
		
		public String alternativeName() {
			return alternativeName;
		}
		
		public boolean isVirtual() {
			return isVirtual;
		}
		
		public static final class Builder {
			
			private String name;
			private String alternativeName;
			private boolean isVirtual;
			
			public AudioDevice build() {
				return new AudioDevice(name, alternativeName, isVirtual);
			}
			
			public void clear() {
				name = null;
				alternativeName = null;
				isVirtual = false;
			}
			
			public void name(String name) {
				this.name = Objects.requireNonNull(name);
			}
			
			public void alternativeName(String alternativeName) {
				this.alternativeName = Objects.requireNonNull(alternativeName);
			}
			
			public void isVirtual(boolean isVirtual) {
				this.isVirtual = isVirtual;
			}
			
			public String name() {
				return name;
			}
			
			public String alternativeName() {
				return alternativeName;
			}
			
			public boolean isVirtual() {
				return isVirtual;
			}
		}
	}
	
	private static final class DirectShowAudioDevicesParser implements Consumer<String> {
		
		private static final Pattern PATTERN_NAME
			= Pattern.compile("^\\[dshow\\s+[^\\]]+\\]\\s+\"([^\"]+)\"\\s+\\(([^\\)]+)\\)\\s*$");
		private static final Pattern PATTERN_ALT_NAME
			= Pattern.compile("^\\[dshow\\s+[^\\]]+\\]\\s+Alternative name \"([^\"]+)\"\\s*$");
		
		private final List<AudioDevice> devices = new ArrayList<>();
		private AudioDevice.Builder audioDevice;
		private boolean wasAudioDevice;
		
		@Override
		public void accept(String line) {
			Matcher matcher;
			if((matcher = PATTERN_NAME.matcher(line)).matches()) {
				// Ignore non-audio devices
				if(!matcher.group(2).equalsIgnoreCase("audio"))
					return;
				
				// Mark the current device as an audio device
				wasAudioDevice = true;
				
				// Audio device name is first, construct or clear the builder as needed
				if(audioDevice == null) {
					audioDevice = new AudioDevice.Builder();
				} else {
					audioDevice.clear();
				}
				
				// Obtain the audio device's name
				audioDevice.name(matcher.group(1));
			} else if(wasAudioDevice && (matcher = PATTERN_ALT_NAME.matcher(line)).matches()) {
				// Obtain the audio device's alternative name
				audioDevice.alternativeName(matcher.group(1));
				// Check whether the device is virtual or not
				audioDevice.isVirtual(isVirtualDevice(audioDevice));
				
				// We've got all the information of the audio device, add it to the list
				devices.add(audioDevice.build());
			}
		}
		
		public List<AudioDevice> devices() {
			return devices;
		}
	}
}