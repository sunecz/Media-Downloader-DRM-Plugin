package sune.app.mediadownloader.drm.util;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.cef.browser.CefFrame;

import sune.app.mediadown.Shared;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMBrowser;
import sune.app.mediadownloader.drm.integration.IntegrationUtils;
import sune.app.mediadownloader.drm.util.DRMUtils.BBox;
import sune.app.mediadownloader.drm.util.DRMUtils.JSRequest;
import sune.app.mediadownloader.drm.util.DRMUtils.Point2D;
import sune.util.ssdf2.SSDCollection;

public final class JS {
	
	// Forbid anyone to create an instance of this class
	private JS() {
	}
	
	private static final String escape(String string, String chars) {
		return string.replaceAll("((?<!\\\\)[" + chars + "])", "\\$1");
	}
	
	private static final String escapeQS(String string) {
		return escape(string, "'");
	}
	
	public static final void execute(CefFrame frame, String code) {
		frame.executeJavaScript(Objects.requireNonNull(code), null, 0);
	}
	
	public static final boolean include(CefFrame frame, String path) {
		return include(frame, path, null);
	}
	
	public static final boolean include(CefFrame frame, String path, Consumer<Exception> onError) {
		return Utils.ignoreWithCheck(() -> {
			try(InputStream stream = IntegrationUtils.resourceStream(path)) {
				execute(frame, new String(stream.readAllBytes(), Shared.CHARSET));
			}
		}, onError);
	}
	
	public static final class Record {
		
		// Forbid anyone to create an instance of this class
		private Record() {
		}
		
		public static final void include(CefFrame frame) {
			JS.include(frame, "/resources/drm/record.js");
		}
		
		public static final void activate(CefFrame frame, String selector) {
			JS.execute(frame, String.format("MediaDownloader.DRM.Record.activate('%s');", escapeQS(selector)));
		}
	}
	
	public static final class Helper {
		
		// Forbid anyone to create an instance of this class
		private Helper() {
		}
		
		public static final void include(CefFrame frame) {
			JS.include(frame, "/resources/drm/helper.js");
		}
		
		public static final void hideVideoElementStyle(CefFrame frame) {
			JS.execute(frame, "MediaDownloader.DRM.Helper.hideVideoElementStyle();");
		}
		
		public static final void click(DRMBrowser browser, CefFrame frame, String selector) {
			String code = String.format(
				"MediaDownloader.DRM.Helper.click('%s', (bbox) => ret(0, bbox));",
				escapeQS(selector)
			);
			browser.addJSRequest(new JSRequest(Request.requestId("click"), code, (result) -> {
				Point2D center = (new BBox((SSDCollection) result)).center();
				browser.accessor().click(center.x, center.y);
			}).send(frame));
		}
	}
	
	public static final class Request {
		
		private static final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
		
		// Forbid anyone to create an instance of this class
		private Request() {
		}
		
		private static final int nextId(String prefix) {
			return counters.computeIfAbsent(prefix, (k) -> new AtomicInteger()).getAndIncrement();
		}
		
		public static final String requestId(String prefix) {
			return prefix + '-' + nextId(prefix);
		}
	}
}