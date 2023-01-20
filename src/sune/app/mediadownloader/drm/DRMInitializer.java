package sune.app.mediadownloader.drm;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.gui.Dialog;
import sune.app.mediadown.language.Translation;
import sune.app.mediadown.util.FXUtils;
import sune.app.mediadownloader.drm.integration.DRMPluginConfiguration;
import sune.app.mediadownloader.drm.util.JCEF;
import sune.app.mediadownloader.drm.util.VirtualAudio;

public final class DRMInitializer {
	
	private static final AtomicBoolean wasRunClasses = new AtomicBoolean();
	
	private static Logger logger;
	
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
		logger = DRMLog.get();
	}
	
	public static final void initializeVirtualAudio() throws Exception {
		DRMPluginConfiguration configuration = DRMPluginConfiguration.instance();
		boolean isVirtualAudioDeviceAllowed = configuration.audioAllowVirtualDevice();
		
		if(logger.isDebugEnabled()) {
			logger.debug("Custom virtual audio device is allowed: {}", isVirtualAudioDeviceAllowed);
		}
		
		// Prepare the custom virtual audio device
		if(isVirtualAudioDeviceAllowed) {
			boolean isVirtualAudioDeviceRegistered = VirtualAudio.isRegistered();
			
			if(logger.isDebugEnabled()) {
				logger.debug("Custom virtual audio device is registered: {}", isVirtualAudioDeviceRegistered);
			}
			
			// Prevent multiple virtual audio device registration
			if(!isVirtualAudioDeviceRegistered) {
				boolean success = true;
				Exception exception = null;
				
				if(logger.isDebugEnabled()) {
					logger.debug("Registering custom virtual audio device...");
				}
				
				try {
					int exitCode = VirtualAudio.register();
					
					if(exitCode == VirtualAudio.RESULT_FAIL_PROC_CALL) {
						if(FXUtils.isInitialized()) {
							Translation tr = MediaDownloader.translation().getTranslation(
								"plugin.drm.error.virtual_device.register"
							);
							Dialog.showError(tr.getSingle("title"), tr.getSingle("text"));
						} else {
							exception = new IllegalStateException(
								"DllRegisterServer failed. Run as an administrator."
							);
						}
					}
					
					success = exitCode == 0;
				} catch(Exception ex) {
					success = false;
					exception = ex;
				}
				
				if(logger.isDebugEnabled()) {
					logger.debug("Custom virtual audio device register result: success={}.", success);
				}
				
				if(!success && !FXUtils.isInitialized()) {
					Exception softException = new IllegalStateException(
						"Unable to register custom virtual audio device.",
						exception
					);
					
					// Do not throw the exception, just notice the user
					MediaDownloader.error(softException);
				}
			}
			
			if(logger.isDebugEnabled()) {
				logger.debug("Custom virtual audio device name: {}", VirtualAudio.audioDeviceName());
			}
		}
	}
}