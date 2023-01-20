package sune.app.mediadownloader.drm.util;

import java.nio.file.Path;

import sune.api.process.Processes;
import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.util.PathSystem;
import sune.app.mediadown.util.Utils;

public final class VirtualAudio {
	
	private static final String DEVICE_NAME = "md-virtual-audio";
	private static final String REG_KEY = "HKLM\\SOFTWARE\\Classes\\CLSID\\{488F9CC6-F14B-4DDC-956A-40734EC20863}";
	private static final String DLL_NAME = "md-virtual-audio.dll";
	
	private static Path pathReg;
	private static Path pathRegsvr32;
	private static Path pathDLL;
	
	public static final int RESULT_OK = 0;
	public static final int RESULT_FAIL_PROC_CALL = 5;
	
	// Forbid anyone to create an instance of this class
	private VirtualAudio() {
	}
	
	private static final Path pathReg() {
		if(pathReg == null) {
			pathReg = Path.of("C:\\Windows\\System32\\reg.exe");
		}
		
		return pathReg;
	}
	
	private static final Path pathRegsvr32() {
		if(pathRegsvr32 == null) {
			pathRegsvr32 = Path.of("C:\\Windows\\System32\\regsvr32.exe");
		}
		
		return pathRegsvr32;
	}
	
	private static final Path pathDLL() {
		if(pathDLL == null) {
			pathDLL = PathSystem.getPath(VirtualAudio.class, "resources/binary/drm/" + DLL_NAME);
		}
		
		return pathDLL;
	}
	
	private static final int execute(Path pathProgram, String args) throws Exception {
		try(ReadOnlyProcess process = Processes.createSynchronous(pathProgram)) {
			process.execute(args);
			return process.waitFor();
		}
	}
	
	private static final boolean regKeyExists(String key) throws Exception {
		return execute(pathReg(), Utils.format("query %{key}s", "key", key)) == RESULT_OK;
	}
	
	private static final int registerDLL(Path dllPath) throws Exception {
		return execute(pathRegsvr32(), Utils.format("/s \"%{dll_path}s\"", "dll_path", dllPath.toString()));
	}
	
	private static final int unregisterDLL(Path dllPath) throws Exception {
		return execute(pathRegsvr32(), Utils.format("/s /u \"%{dll_path}s\"", "dll_path", dllPath.toString()));
	}
	
	public static final boolean isRegistered() throws Exception {
		return regKeyExists(REG_KEY);
	}
	
	public static final int register() throws Exception {
		return registerDLL(pathDLL());
	}
	
	public static final int unregister() throws Exception {
		return unregisterDLL(pathDLL());
	}
	
	public static final String audioDeviceName() {
		return DEVICE_NAME;
	}
}