package sune.app.mediadownloader.drm.util;

import org.cef.browser.CefFrame;

import sune.app.mediadownloader.drm.DRMBrowser;
import sune.app.mediadownloader.drm.util.DRMUtils.JSRequest;

public final class PlaybackController {
	
	private final DRMBrowser browser;
	private final CefFrame frame;
	private final int videoID;
	
	public PlaybackController(DRMBrowser browser, CefFrame frame, int videoID) {
		this.browser = browser;
		this.frame = frame;
		this.videoID = videoID;
	}
	
	public void play() { play(null); }
	public void pause() { pause(null); }
	public void setTime(double time) { setTime(time, null); }
	
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
		browser.addJSRequest(new JSRequest("vc-play", jsCode, (data) -> {
			int x = browser.getWidth() / 2;
			int y = browser.getHeight() / 2;
			browser.accessor().click(x, y);
		}, (data) -> {
			if(callback != null) callback.run();
		}).send(frame));
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
		browser.addJSRequest(new JSRequest("vc-pause", jsCode, (data) -> {
			int x = browser.getWidth() / 2;
			int y = browser.getHeight() / 2;
			browser.accessor().click(x, y);
		}, (data) -> {
			if(callback != null) callback.run();
		}).send(frame));
	}
	
	public void setTime(double time, Runnable callback) {
		String jsCode = DRMUtils.format(""
			+ "let video = document.querySelector('video[data-vid=\"" + videoID + "\"]');"
			+ "if(Math.abs(video.currentTime - %1$.6f) <= 0.000001) {"
			+ "    ret(0, {});"
			+ "} else {"
			+ "    let seeked = ((e) => {"
			+ "        if(Math.abs(video.currentTime - %1$.6f) <= 0.000001) {"
			+ "            video.removeEventListener('seeked', seeked, true);"
			+ "            ret(0, {});"
			+ "        }"
			+ "    });"
			+ "    video.addEventListener('seeked', seeked, true);"
			+ "    video.currentTime = %1$.6f;"
			+ "}",
			time);
		// Do user gesture (interaction)
		browser.addJSRequest(new JSRequest("vc-settime", jsCode, (data) -> {
			if(callback != null) callback.run();
		}).send(frame));
	}
}