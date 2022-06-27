package sune.app.mediadownloader.drm;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import sune.app.mediadownloader.drm.util.JCEF;

public final class DRMInitializer {
	
	private static final AtomicBoolean wasRunClasses = new AtomicBoolean();
	
	// Forbid anyone to create an instance of this class
	private DRMInitializer() {
	}
	
	public static final void initializeClasses() {
		// Return, if already run, otherwise set to true
		if(!wasRunClasses.compareAndSet(false, true))
			return;
		DRMBootstrap bootstrap = DRMBootstrap.instance();
		if(bootstrap == null)
			throw new IllegalStateException("DRM system not yet initialized");
		boolean isDebug = bootstrap.debug();
		Path logFile = bootstrap.logFile();
		DRMLog.enable(isDebug, logFile);
		DRMLog.disableInternalWarnings();
		DRM.setDebug(isDebug);
		DRM.setLogFile(logFile);
		JCEF.disablePrintToConsole();
	}
}