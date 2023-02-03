package sune.app.mediadownloader.drm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.CefSettings.LogSeverity;
import org.cef.browser.CefBrowser;
import org.littleshoot.proxy.mitm.RootCertificateException;
import org.slf4j.Logger;

import sune.app.mediadown.util.PathSystem;
import sune.app.mediadown.util.Reflection2;
import sune.app.mediadown.util.StateMutex;
import sune.app.mediadown.util.Threads;
import sune.app.mediadownloader.drm.util.CEFLog;

public final class DRM {
	
	private static final Logger logger = DRMLog.get();
	
	private static boolean DEBUG;
	private static Path LOG_FILE;
	private static boolean CEF_LOG_ENABLED = false;
	
	private static final String[] cefAppArgs;
	private static boolean cefStarted;
	private static CefApp cefApp;
	private static int cefBrowserCtr;
	private static final StateMutex mtxDispose = new StateMutex();
	
	static {
		String dirTemp = Path.of(System.getProperty("java.io.tmpdir") + "/cache").toAbsolutePath().toString();
		if(logger.isDebugEnabled())
			logger.debug("Cache directory: {}", dirTemp);
		List<String> list = new ArrayList<>();
		list.add("--enable-widevine-cdm");
		list.add("--cache-path=" + dirTemp); // Must be specified for the Widevine CDM
		list.add("--component-updater=fast-update"); // Start downloading Widevine CDM almost immediately after startup
		list.add("--autoplay-policy=no-user-gesture-required");
		list.add("--disable-gpu"); // Enable software rendering only to be able to record the video output
		list.add("--disable-gpu-vsync");
		list.add("--disable-gpu-compositing");
		list.add("--proxy-server=" + DRMConstants.PROXY_DOMAIN + ":" + DRMConstants.PROXY_PORT);
		list.add("--ignore-certificate-errors");
		cefAppArgs = list.toArray(String[]::new);
	}
	
	private static final void ensureCefStarted() {
		if(!cefStarted) {
			if(!CefApp.startup(cefAppArgs))
				throw new IllegalStateException("CEF startup failed");
			// Add automatic dispose on shutdown
			Runtime.getRuntime().addShutdownHook(Threads.newThreadUnmanaged(DRM::dispose));
			cefStarted = true;
		}
	}
	
	protected static final void browserCreated(CefBrowser browser) {
		++cefBrowserCtr;
	}
	
	protected static final void browserClosed(CefBrowser browser) {
		if(--cefBrowserCtr == 0) {
			mtxDispose.unlock();
		}
	}
	
	public static final void setDebug(boolean flag) {
		DEBUG = flag;
	}
	
	public static final boolean isDebug() {
		return DEBUG;
	}
	
	public static final void setLogFile(Path path) {
		LOG_FILE = path.toAbsolutePath();
	}
	
	public static final Path getLogFile() {
		return LOG_FILE;
	}
	
	public static final String[] getDefaultArgs() {
		return Arrays.copyOf(cefAppArgs, cefAppArgs.length);
	}
	
	public static final CefApp getApplication() {
		ensureCefStarted();
		
		if(CefApp.getState() == CefApp.CefAppState.INITIALIZED) {
			return cefApp;
		}
		
		CefSettings settings = new CefSettings();
		boolean cefLogEnabled = isCefLogEnabled();
		Path pathLogFile = LOG_FILE;
		
		// For now, force the log file to be present
		if(pathLogFile == null) {
			throw new IllegalStateException("Must specify the log file");
		}
		
		settings.windowless_rendering_enabled = false;
		settings.log_severity = LogSeverity.LOGSEVERITY_ERROR; // Always log at least errors
		settings.log_file = pathLogFile.toString();
		
		if(DEBUG) {
			settings.remote_debugging_port = 8080;
		}
		
		if(DEBUG || cefLogEnabled) {
			settings.log_severity = LogSeverity.LOGSEVERITY_VERBOSE;
			
			if(cefLogEnabled) {
				// Manual Widevine CDM download requires CefLog functionality
				CEFLog.initialize(pathLogFile);
			}
		}
		
		if(logger.isDebugEnabled()) {
			logger.debug("Log file: {}", pathLogFile.toString());
		}
		
		cefApp = CefApp.getInstance(cefAppArgs, settings);
		
		return cefApp;
	}
	
	public static final DRMClient createClient(DRMContext context, DRMResolver resolver) {
		CefClient client = getApplication().createClient();
		
		DRMProxy proxy;
		try {
			proxy = new DRMProxy(DRMConstants.PROXY_PORT, resolver).create();
		} catch(RootCertificateException | IOException ex) {
			throw new IllegalStateException("Unable to create proxy", ex);
		}
		
		return new DRMClient(client, context, proxy, resolver);
	}
	
	public static final void setCefLogEnabled(boolean enabled) {
		CEF_LOG_ENABLED = enabled;
	}
	
	public static final boolean isCefLogEnabled() {
		return CEF_LOG_ENABLED;
	}
	
	public static final Path cefLogFile() {
		String path = "debug.log";
		CefApp app;
		if((app = CefApp.getInstance()) != null) {
			CefSettings settings = Reflection2.getField(CefApp.class, app, "settings_");
			if(settings != null) path = settings.log_file;
		}
		return PathSystem.getPath(DRM.class, path);
	}
	
	public static final void dispose() {
		while(cefBrowserCtr > 0) {
			mtxDispose.awaitAndReset();
		}
		
		getApplication().dispose();
	}
}