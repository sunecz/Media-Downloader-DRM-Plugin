package sune.app.mediadownloader.drm;

import java.nio.charset.Charset;

import org.cef.browser.CefFrame;

import sune.util.ssdf2.SSDCollection;

public interface DRMResolver {
	
	void onLoadStart(DRMBrowser browser, CefFrame frame);
	void onLoadEnd(DRMBrowser browser, CefFrame frame, int httpStatusCode);
	void onRequest(DRMBrowser browser, CefFrame frame, String requestName, SSDCollection json, String request);
	
	boolean shouldModifyResponse(String uri, String mimeType, Charset charset);
	String modifyResponse(String uri, String mimeType, Charset charset, String content);
	
	String url();
}