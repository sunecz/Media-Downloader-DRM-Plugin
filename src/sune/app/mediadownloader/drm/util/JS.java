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
import sune.app.mediadownloader.drm.util.DRMUtils.Promise;
import sune.util.ssdf2.SSDCollection;
import sune.util.ssdf2.SSDNode;

public final class JS {
	
	// Forbid anyone to create an instance of this class
	private JS() {
	}
	
	private static final String escape(String string, String chars) {
		return string.replaceAll("((?<!\\\\)[" + chars + "])", "\\\\$1");
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
			JS.execute(frame, DRMUtils.format("MediaDownloader.DRM.Record.activate('%s');", escapeQS(selector)));
		}
	}
	
	public static final class Helper {
		
		// Forbid anyone to create an instance of this class
		private Helper() {
		}
		
		public static final void include(CefFrame frame) {
			JS.include(frame, "/resources/drm/helper.js");
		}
		
		public static final void includeStyle(CefFrame frame, String content) {
			JS.execute(frame, DRMUtils.format("MediaDownloader.DRM.Helper.includeStyle('%s');", escapeQS(content)));
		}
		
		public static final void hideVideoElementStyle(CefFrame frame) {
			JS.execute(frame, "MediaDownloader.DRM.Helper.hideVideoElementStyle();");
		}
		
		public static final void click(DRMBrowser browser, CefFrame frame, String selector) {
			String code = DRMUtils.format(
				"MediaDownloader.DRM.Helper.click('%s', (bbox) => ret(0, bbox));",
				escapeQS(selector)
			);
			browser.addJSRequest(frame, new JSRequest(Request.requestId("click"), code, (result) -> {
				browser.accessor().click((new BBox((SSDCollection) result)).center());
			}));
		}
		
		public static final void enableDoUserInteraction(DRMBrowser browser, CefFrame frame) {
			browser.addJSRequest(frame, JSRequest.ofNoop("doUserInteraction", (data) -> {
				browser.accessor().click(browser.center());
			}));
		}
		
		public static final void enableInterframeCommunication(CefFrame frame) {
			JS.execute(frame, "MediaDownloader.DRM.Helper.enableInterframeCommunication();");
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
		
		@SafeVarargs
		public static final JSRequest ofNoop(String prefix, Consumer<SSDNode>... callbacks) {
			return JSRequest.ofNoop(requestId(prefix), callbacks);
		}
		
		@SafeVarargs
		public static final JSRequest of(String prefix, String jsCode, Consumer<SSDNode>... callbacks) {
			return JSRequest.of(requestId(prefix), jsCode, callbacks);
		}
	}
	
	public static final class Playback {
		
		// Forbid anyone to create an instance of this class
		private Playback() {
		}
		
		public static final void include(CefFrame frame) {
			JS.include(frame, "/resources/drm/playback.js");
		}
		
		private static final Promise.OfVoid muted(DRMBrowser browser, CefFrame frame, int videoId, boolean muted) {
			Promise.OfVoid promise = new Promise.OfVoid();
			String code = DRMUtils.format("MediaDownloader.DRM.Playback.muted('%1$s', %2$b, ret);", videoId, muted);
			browser.addJSRequest(frame, JS.Request.of("playback-muted", code, (data) -> promise.resolve()));
			return promise;
		}
		
		public static final Promise.OfVoid play(DRMBrowser browser, CefFrame frame, int videoId) {
			Promise.OfVoid promise = new Promise.OfVoid();
			String code = DRMUtils.format("MediaDownloader.DRM.Playback.play('%1$s', ret);", videoId);
			browser.addJSRequest(frame, JS.Request.of("playback-play", code, (data) -> promise.resolve()));
			return promise;
		}
		
		public static final Promise.OfVoid pause(DRMBrowser browser, CefFrame frame, int videoId) {
			Promise.OfVoid promise = new Promise.OfVoid();
			String code = DRMUtils.format("MediaDownloader.DRM.Playback.pause('%1$s', ret);", videoId);
			browser.addJSRequest(frame, JS.Request.of("playback-pause", code, (data) -> promise.resolve()));
			return promise;
		}
		
		public static final Promise.OfVoid time(DRMBrowser browser, CefFrame frame, int videoId, double time, boolean keepPaused) {
			Promise.OfVoid promise = new Promise.OfVoid();
			String code = DRMUtils.format("MediaDownloader.DRM.Playback.time('%1$s', %2$.6f, %3$b, ret);", videoId, time, keepPaused);
			browser.addJSRequest(frame, JS.Request.of("playback-time", code, (data) -> promise.resolve()));
			return promise;
		}
		
		public static final Promise.OfVoid mute(DRMBrowser browser, CefFrame frame, int videoId) {
			return muted(browser, frame, videoId, true);
		}
		
		public static final Promise.OfVoid unmute(DRMBrowser browser, CefFrame frame, int videoId) {
			return muted(browser, frame, videoId, false);
		}
		
		public static final Promise.OfVoid volume(DRMBrowser browser, CefFrame frame, int videoId, double volume) {
			Promise.OfVoid promise = new Promise.OfVoid();
			String code = DRMUtils.format("MediaDownloader.DRM.Playback.volume('%1$s', %2$.6f, ret);", videoId, volume);
			browser.addJSRequest(frame, JS.Request.of("playback-volume", code, (data) -> promise.resolve()));
			return promise;
		}
		
		public static final Promise.OfRef<Boolean> isPlaying(DRMBrowser browser, CefFrame frame, int videoId) {
			Promise.OfRef<Boolean> promise = new Promise.OfRef<>();
			String code = DRMUtils.format("MediaDownloader.DRM.Playback.isPlaying('%1$s', ret);", videoId);
			browser.addJSRequest(frame, JS.Request.of("playback-isPlaying", code, (data) -> {
				boolean isPlaying = ((SSDCollection) data).getDirectBoolean("is_playing", false);
				promise.resolve(isPlaying);
			}));
			return promise;
		}
	}
}