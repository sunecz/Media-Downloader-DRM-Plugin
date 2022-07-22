package sune.app.mediadownloader.drm.util;

import org.cef.browser.CefFrame;

import sune.app.mediadownloader.drm.DRMBrowser;
import sune.app.mediadownloader.drm.DRMLog;
import sune.app.mediadownloader.drm.util.DRMUtils.JSRequest;

public final class PlaybackController {
	
	private final DRMBrowser browser;
	private final CefFrame frame;
	private final int videoID;
	
	public PlaybackController(DRMBrowser browser, CefFrame frame, int videoID) {
		this.browser = browser;
		this.frame = frame;
		this.videoID = videoID;
		
		DRMLog.get().debug("Included for frame: {}", frame.getIdentifier());
		
		JS.Playback.include(frame);
		browser.addJSRequest(frame, JSRequest.ofNoop("doUserInteraction", (data) -> {
			int x = browser.getWidth() / 2;
			int y = browser.getHeight() / 2;
			browser.accessor().click(x, y);
		}));
	}
	
	public void play() { play(null); }
	public void pause() { pause(null); }
	public void time(double time, boolean keepPaused) { time(time, keepPaused, null); }
	
	public void play(Runnable callback) {
		String jsCode = ""
				+ "let click = ((e) => {"
				+ "    document.removeEventListener('click', click, true);"
				+ "    let video = document.querySelector('video[data-vid=\"" + videoID + "\"]');"
				+ "    let playing = ((e) => {"
				+ "        video.removeEventListener('play', playing, true);"
				+ "        ret(1, {});"
				+ "    });"
				+ "    video.addEventListener('play', playing, true);"
				+ "    video.play();"
				+ "});"
				+ "document.addEventListener('click', click, true);"
				+ "ret(0, {});";
		// Do user gesture (interaction)
		browser.addJSRequest(frame, new JSRequest("vc-play", jsCode, (data) -> {
			int x = browser.getWidth() / 2;
			int y = browser.getHeight() / 2;
			browser.accessor().click(x, y);
		}, (data) -> {
			if(callback != null) callback.run();
		}));
	}
	
	public void pause(Runnable callback) {
		String jsCode = ""
				+ "let click = ((e) => {"
				+ "    document.removeEventListener('click', click, true);"
				+ "    let video = document.querySelector('video[data-vid=\"" + videoID + "\"]');"
				+ "    let paused = ((e) => {"
				+ "        video.removeEventListener('pause', paused, true);"
				+ "        ret(1, {});"
				+ "    });"
				+ "    video.addEventListener('pause', paused, true);"
				+ "    video.pause();"
				+ "});"
				+ "document.addEventListener('click', click, true);"
				+ "ret(0, {});";
		// Do user gesture (interaction)
		browser.addJSRequest(frame, new JSRequest("vc-pause", jsCode, (data) -> {
			int x = browser.getWidth() / 2;
			int y = browser.getHeight() / 2;
			browser.accessor().click(x, y);
		}, (data) -> {
			if(callback != null) callback.run();
		}));
	}
	
	private static final void maybeCall(Runnable callback) {
		if(callback != null) callback.run();
	}
	
	public void time(double time, boolean keepPaused, Runnable callback) {
		String code = DRMUtils.format("MediaDownloader.DRM.Playback.time('%1$s', %2$.6f, %3$b, ret);", videoID, time, keepPaused);
		browser.addJSRequest(frame, JS.Request.of("playback-time", code, (data) -> maybeCall(callback)));
	}
	
	private final void muted(boolean muted, Runnable callback) {
		String code = DRMUtils.format("MediaDownloader.DRM.Playback.muted('%1$s', %2$b, ret);", videoID, muted);
		browser.addJSRequest(frame, JS.Request.of("playback-muted", code, (data) -> maybeCall(callback)));
	}
	
	public void mute(Runnable callback) {
		muted(true, callback);
	}
	
	public void unmute(Runnable callback) {
		muted(false, callback);
	}
	
	public void volume(double volume, Runnable callback) {
		String code = DRMUtils.format("MediaDownloader.DRM.Playback.volume('%1$s', %2$.6f, ret);", videoID, volume);
		browser.addJSRequest(frame, JS.Request.of("playback-volume", code, (data) -> maybeCall(callback)));
	}
	
	public void mute() { mute(null); }
	public void unmute() { unmute(null); }
	public void volume(double volume) { volume(volume, null); }
}