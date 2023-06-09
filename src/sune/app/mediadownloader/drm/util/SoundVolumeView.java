package sune.app.mediadownloader.drm.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sune.api.process.Processes;
import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.util.JSON;
import sune.app.mediadown.util.JSON.JSONCollection;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.PathSystem;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.util.AudioDevices.AudioDevice;
import sune.app.mediadownloader.drm.util.AudioDevices.AudioDevice.Direction;

// Sound Volume View (https://www.nirsoft.net/utils/sound_volume_view.html)
public final class SoundVolumeView {
	
	private static Path path;
	
	// Forbid anyone to create an instance of this class
	private SoundVolumeView() {
	}
	
	private static final Path binaryPath() {
		if(path == null) {
			path = Path.of(PathSystem.getFullPath(SoundVolumeView.class, "resources/binary/drm"), "SoundVolumeView.exe");
			
			if(!NIO.isRegularFile(path)) {
				throw new IllegalStateException("SoundVolumeView.exe was not found at: " + path.toAbsolutePath().toString());
			}
		}
		
		return path;
	}
	
	public static final boolean setAudioOutput(long pid, String deviceName) throws Exception {
		try(ReadOnlyProcess process = Processes.createSynchronous(binaryPath())) {
			process.execute(Utils.format(
				"/SetAppDefault \"%{device_name}s\" all %{pid}d",
				"device_name", deviceName,
				"pid", pid
			));
			
			return process.waitFor() == 0;
		}
	}
	
	public static final Set<Long> audioApplicationPIDs() throws Exception {
		Set<Long> pids = new HashSet<>();
		
		try(ReadOnlyProcess process = Processes.createSynchronous(binaryPath())) {
			Path output = NIO.tempFile("svv-audio_apps_pids-", ".json");
			
			process.execute(Utils.format(
				"/SaveFileEncoding 3 /Columns Type,ProcessID /sjson \"%{output}s\"",
				"output", output.toAbsolutePath().toString()
			));
			
			try(InputStream stream = Files.newInputStream(output, StandardOpenOption.READ)) {
				JSONCollection json = JSON.read(stream);
				
				for(JSONCollection item : json.collectionsIterable()) {
					String itemType = item.getString("Type", "");
					
					if(!itemType.equals("Application")) {
						continue;
					}
					
					String itemPID = item.getString("Process ID", "");
					
					if(!itemPID.isEmpty()) {
						pids.add(Long.valueOf(itemPID));
					}
				}
			}
		}
		
		return pids;
	}
	
	public static final List<AudioDevice> audioDevices() throws Exception {
		List<AudioDevice> audioDevices = new ArrayList<>();
		
		try(ReadOnlyProcess process = Processes.createSynchronous(binaryPath())) {
			Path output = NIO.tempFile("svv-audio_devices-", ".json");
			
			process.execute(Utils.format(
				"/SaveFileEncoding 3 /Columns Name,DeviceName,Type,Direction,Command-LineFriendlyID /sjson \"%{output}s\"",
				"output", output.toAbsolutePath().toString()
			));
			
			try(InputStream stream = Files.newInputStream(output, StandardOpenOption.READ)) {
				JSONCollection json = JSON.read(stream);
				
				for(JSONCollection item : json.collectionsIterable()) {
					String itemType = item.getString("Type");
					
					if(!itemType.equals("Device")) {
						continue;
					}
					
					String itemName = item.getString("Name", "UNSET");
					String itemDeviceName = item.getString("Device Name", "UNSET");
					String itemDirection = item.getString("Direction", "UNSET");
					String itemFriendlyName = item.getString("Command-Line Friendly ID", "UNSET");
					
					String deviceName = String.format("%s (%s)", itemName, itemDeviceName);
					String deviceAlternativeName = itemFriendlyName;
					Direction deviceDirection = AudioDevice.Direction.of(itemDirection);
					
					AudioDevice audioDevice = AudioDevices.newDevice(deviceName, deviceAlternativeName, deviceDirection);
					audioDevices.add(audioDevice);
				}
			}
		}
		
		return audioDevices;
	}
	
	public static final AudioDevice defaultAudioDevice(Direction direction) throws Exception {
		AudioDevice defaultAudioDevice = null;
		
		try(ReadOnlyProcess process = Processes.createSynchronous(binaryPath())) {
			Path output = NIO.tempFile("svv-default_audio_device-", ".json");
			
			process.execute(Utils.format(
				"/SaveFileEncoding 3 /Columns Name,DeviceName,Type,Default,Command-LineFriendlyID " +
				"/sjson \"%{output}s\"",
				"output", output.toAbsolutePath().toString()
			));
			
			try(InputStream stream = Files.newInputStream(output, StandardOpenOption.READ)) {
				JSONCollection json = JSON.read(stream);
				
				for(JSONCollection item : json.collectionsIterable()) {
					String itemType = item.getString("Type");
					
					if(!itemType.equals("Device")) {
						continue;
					}
					
					String itemName = item.getString("Name", "UNSET");
					String itemDeviceName = item.getString("Device Name", "UNSET");
					String itemDefault = item.getString("Default", "UNSET");
					String itemFriendlyName = item.getString("Command-Line Friendly ID", "UNSET");
					
					String deviceName = String.format("%s (%s)", itemName, itemDeviceName);
					String deviceAlternativeName = itemFriendlyName;
					Direction deviceDefaultDirection = AudioDevice.Direction.of(itemDefault);
					
					if(deviceDefaultDirection != direction) {
						continue;
					}
					
					defaultAudioDevice = AudioDevices.newDevice(
						deviceName, deviceAlternativeName, deviceDefaultDirection
					);
					
					// Device found, no need to continue
					break;
				}
			}
		}
		
		return defaultAudioDevice;
	}
}