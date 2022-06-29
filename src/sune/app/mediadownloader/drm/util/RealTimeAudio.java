package sune.app.mediadownloader.drm.util;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.util.Utils;

public class RealTimeAudio {
	
	private static final Pattern REGEX_TIME   = Pattern.compile("^.*?pts_time:(-?\\d+(?:\\.\\d*)?)$");
	private static final Pattern REGEX_VOLUME = Pattern.compile("^lavfi.astats.Overall.RMS_level=(-?\\d+(?:\\.\\d*)?)$");
	
	private final String audioDeviceName;
	private final ProcessManager processManager;
	private ReadOnlyProcess process;
	private Consumer<AudioVolume> listener;
	
	private AudioVolume volume;
	private AudioVolume temp;
	
	public RealTimeAudio(String audioDeviceName, ProcessManager processManager) {
		this.audioDeviceName = Objects.requireNonNull(audioDeviceName);
		this.processManager = Objects.requireNonNull(processManager);
	}
	
	private final void notifyListener(AudioVolume av) {
		if(listener != null)
			listener.accept(av);
	}
	
	private final void parseLine(String line) {
		Matcher matcher;
		
		if(!temp.isTimeValid()) {
			if((matcher = REGEX_TIME.matcher(line)).matches()) {
				temp.time = Double.valueOf(matcher.group(1));
				
				if(temp.isValid())
					notifyListener(temp.setAndReset(volume));
				
				return; // Line processed
			}
		}
		
		if(!temp.isVolumeValid()) {
			if((matcher = REGEX_VOLUME.matcher(line)).matches()) {
				temp.volume = Double.valueOf(matcher.group(1));
				
				if(temp.isValid())
					notifyListener(temp.setAndReset(volume));
				
				return; // Line processed
			}
		}
	}
	
	public void listen(Consumer<AudioVolume> listener) throws Exception {
		process = processManager.ffmpeg(this::parseLine);
		volume = new AudioVolume();
		temp = new AudioVolume();
		this.listener = listener; // Can be null
		StringBuilder builder = new StringBuilder();
		builder.append(" -f dshow -i audio=\"%{device_name}s\"");
		builder.append(" -af asetnsamples=1000,astats=metadata=1:reset=1,ametadata=print:key=lavfi.astats.Overall.RMS_level:file=-:direct=1");
		builder.append(" -f null -hide_banner -nostats -");
		String command = Utils.format(builder.toString(), "device_name", audioDeviceName);
		process.execute(command);
	}
	
	public AudioVolume volume() {
		return volume;
	}
	
	public static final class AudioVolume {
		
		private double time;
		private double volume;
		
		private AudioVolume() {
			time = Double.NaN;
			volume = Double.NaN;
		}
		
		protected AudioVolume setAndReset(AudioVolume other) {
			other.time = time;
			other.volume = volume;
			time = Double.NaN;
			volume = Double.NaN;
			return other;
		}
		
		public AudioVolume time(double newTime) {
			AudioVolume copy = new AudioVolume();
			copy.time = newTime;
			copy.volume = volume;
			return copy;
		}
		
		public double time() {
			return time;
		}
		
		public double volume() {
			return volume;
		}
		
		protected boolean isTimeValid() {
			return !Double.isNaN(time);
		}
		
		protected boolean isVolumeValid() {
			return !Double.isNaN(volume);
		}
		
		protected boolean isValid() {
			return isTimeValid() && isVolumeValid();
		}
	}
}