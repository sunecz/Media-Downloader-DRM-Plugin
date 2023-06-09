package sune.app.mediadownloader.drm;

import java.nio.charset.Charset;

import org.cef.browser.CefFrame;

import io.netty.handler.codec.http.FullHttpRequest;
import sune.app.mediadown.util.JSON.JSONCollection;

public interface DRMResolver {
	
	void onLoadStart(DRMBrowser browser, CefFrame frame);
	void onLoadEnd(DRMBrowser browser, CefFrame frame, int httpStatusCode);
	void onRequest(DRMBrowser browser, CefFrame frame, String requestName, JSONCollection json, String request);
	
	boolean shouldModifyResponse(String uri, String mimeType, Charset charset, FullHttpRequest request);
	String modifyResponse(String uri, String mimeType, Charset charset, String content, FullHttpRequest request);
	
	default boolean shouldModifyRequest(FullHttpRequest request) { return false; }
	default void modifyRequest(FullHttpRequest request) {}
	
	String url();
}