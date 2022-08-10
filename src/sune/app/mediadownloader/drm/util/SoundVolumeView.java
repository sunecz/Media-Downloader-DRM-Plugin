package sune.app.mediadownloader.drm.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import sune.api.process.Processes;
import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.PathSystem;
import sune.util.ssdf2.SSDCollection;
import sune.util.ssdf2.SSDF;

// Sound Volume View (https://www.nirsoft.net/utils/sound_volume_view.html)
public final class SoundVolumeView {
	
	private static Path fileBinary;
	private static final Path ensureBinary() {
		if((fileBinary == null)) {
			fileBinary = Paths.get(PathSystem.getFullPath(SoundVolumeView.class, "resources/binary/drm"), "SoundVolumeView.exe");
			if(!NIO.isRegularFile(fileBinary))
				throw new IllegalStateException("SoundVolumeView.exe was not found at: " + fileBinary.toAbsolutePath().toString());
		}
		return fileBinary;
	}
	
	public static final boolean setAudioOutput(long pid, String deviceName) throws Exception {
		ensureBinary();
		try(ReadOnlyProcess process = Processes.createSynchronous(fileBinary)) {
			process.execute(DRMUtils.format("/SetAppDefault \"%s\" all %d", deviceName, pid));
			return process.waitFor() == 0;
		}
	}
	
	public static final Set<Long> getAudioAppsPIDs() throws Exception {
		ensureBinary();
		Set<Long> pids = new HashSet<>();
		try(ReadOnlyProcess process = Processes.createSynchronous(fileBinary)) {
			String fileName = UUID.randomUUID() + ".json";
			Path output = Paths.get(PathSystem.getFullPath(SoundVolumeView.class, "resources/binary/drm"), fileName);
			process.execute(DRMUtils.format("/SaveFileEncoding 3 /sjson \"%s\"", output.toAbsolutePath().toString()));
			try(InputStream stream = Files.newInputStream(output, StandardOpenOption.READ)) {
				SSDCollection json = SSDF.readJSON(stream);
				for(SSDCollection pdata : json.collectionsIterable()) {
					String spid = pdata.getDirectString("Process ID");
					if(!spid.isEmpty()) pids.add(Long.valueOf(spid));
				}
			} finally {
				NIO.deleteFile(output);
			}
		}
		return pids;
	}
	
	// Forbid anyone to create an instance of this class
	private SoundVolumeView() {
	}
}