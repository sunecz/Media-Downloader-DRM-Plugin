package sune.app.mediadownloader.drm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.ffmpeg.FFmpeg;
import sune.app.mediadownloader.drm.util.AudioDevices.AudioDevice.Direction;

public final class AudioDevices {
	
	private static List<AudioDevice> dshowDevices;
	
	// Forbid anyone to create an instance of this class
	private AudioDevices() {
	}
	
	public static final boolean isVirtualDevice(String audioDeviceName) {
		String lowerCaseName = audioDeviceName.toLowerCase();
		return (lowerCaseName.contains("cable output") || lowerCaseName.contains("virtual"))
					// Builtin virtual audio device is handled separately
					&& !lowerCaseName.startsWith(VirtualAudio.audioDeviceName());
	}
	
	public static final List<AudioDevice> directShowDevices() throws Exception {
		if(dshowDevices == null) {
			DirectShowAudioDevicesParser parser = new DirectShowAudioDevicesParser();
			
			try(ReadOnlyProcess process = FFmpeg.createAsynchronousProcess(parser)) {
				String command = "-f dshow -list_devices true -i dummy -hide_banner";
				process.execute(command);
				DRMProcessUtils.throwIfFailedFFMpeg(process.waitFor(), 1);
			}
			
			dshowDevices = parser.devices();
		}
		
		return dshowDevices;
	}
	
	public static final List<AudioDevice> captureAudioDevices() throws Exception {
		return directShowDevices();
	}
	
	public static final List<AudioDevice> renderAudioDevices() throws Exception {
		return SoundVolumeView.audioDevices().stream()
					.filter((d) -> d.direction() == Direction.RENDER)
					.collect(Collectors.toList());
	}
	
	public static final AudioDevice stereoMixDevice() throws Exception {
		return directShowDevices().stream()
					.filter((d) -> d.name().contains("stereo"))
					.findFirst().orElse(null);
	}
	
	public static final AudioDevice virtualDevice() throws Exception {
		return directShowDevices().stream()
					.filter((d) -> isVirtualDevice(d.name()))
					.findFirst().orElse(null);
	}
	
	public static final AudioDevice newDevice(String name, String alternativeName, AudioDevice.Direction direction) {
		return AudioDevice.builder()
					.name(name)
					.alternativeName(alternativeName)
					.direction(direction)
					.build();
	}
	
	public static final AudioDevice findOfName(String name) throws Exception {
		return directShowDevices().stream()
					.filter((d) -> d.name().equals(name))
					.findFirst().orElse(null);
	}
	
	public static final AudioDevice findOfAlternativeName(String alternativeName) throws Exception {
		return directShowDevices().stream()
					.filter((d) -> d.alternativeName().equals(alternativeName))
					.findFirst().orElse(null);
	}
	
	public static final AudioDevice defaultCaptureAudioDevice() throws Exception {
		return SoundVolumeView.defaultAudioDevice(Direction.CAPTURE);
	}
	
	public static final AudioDevice defaultRenderAudioDevice() throws Exception {
		return SoundVolumeView.defaultAudioDevice(Direction.RENDER);
	}
	
	public static final class AudioDevice {
		
		private final String name;
		private final String alternativeName;
		private final Direction direction;
		
		private AudioDevice(String name, String alternativeName, Direction direction) {
			this.name = Objects.requireNonNull(name);
			this.alternativeName = Objects.requireNonNull(alternativeName);
			this.direction = direction;
		}
		
		public static final Builder builder() {
			return new Builder();
		}
		
		public String name() {
			return name;
		}
		
		public String alternativeName() {
			return alternativeName;
		}
		
		public Direction direction() {
			return direction;
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(alternativeName, direction, name);
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			AudioDevice other = (AudioDevice) obj;
			return Objects.equals(alternativeName, other.alternativeName)
			        && direction == other.direction
			        && Objects.equals(name, other.name);
		}
		
		@Override
		public String toString() {
			return String.format(
				"AudioDevice(name=%s, alternativeName=%s, direction=%s)",
				name, alternativeName, direction
			);
		}
		
		public static final class Builder {
			
			private String name;
			private String alternativeName;
			private Direction direction;
			
			private Builder() {
				clear();
			}
			
			public AudioDevice build() {
				return new AudioDevice(name, alternativeName, direction);
			}
			
			public final void clear() {
				name = null;
				alternativeName = null;
				direction = Direction.UNKNOWN;
			}
			
			public Builder name(String name) {
				this.name = Objects.requireNonNull(name);
				return this;
			}
			
			public Builder alternativeName(String alternativeName) {
				this.alternativeName = Objects.requireNonNull(alternativeName);
				return this;
			}
			
			public Builder direction(Direction direction) {
				this.direction = direction;
				return this;
			}
			
			public String name() {
				return name;
			}
			
			public String alternativeName() {
				return alternativeName;
			}
			
			public Direction direction() {
				return direction;
			}
		}
		
		public static enum Direction {
			
			UNKNOWN, RENDER, CAPTURE;
			
			private static List<Direction> values;
			
			public static final List<Direction> allValues() {
				if(values == null) {
					values = List.of(values());
				}
				
				return values;
			}
			
			public static final Direction of(String string) {
				String normalized = string.strip().toUpperCase();
				
				return allValues().stream()
							.filter((e) -> e.name().equals(normalized))
							.findFirst().orElse(UNKNOWN);
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
					audioDevice = AudioDevice.builder();
				} else {
					audioDevice.clear();
				}
				
				// Obtain the audio device's name
				audioDevice.name(matcher.group(1));
			} else if(wasAudioDevice && (matcher = PATTERN_ALT_NAME.matcher(line)).matches()) {
				// Obtain the audio device's alternative name
				audioDevice.alternativeName(matcher.group(1));
				// Check whether the device is virtual or not
				audioDevice.direction(AudioDevice.Direction.CAPTURE);
				
				// We've got all the information of the audio device, add it to the list
				devices.add(audioDevice.build());
			}
		}
		
		public List<AudioDevice> devices() {
			return devices;
		}
	}
}