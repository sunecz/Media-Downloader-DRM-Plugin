package sune.app.mediadownloader.drm;

public interface DRMBrowserContext {
	
	void close();
	DRMBrowser browser();
	String title();
}