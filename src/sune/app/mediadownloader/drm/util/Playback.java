package sune.app.mediadownloader.drm.util;

import org.cef.browser.CefFrame;

import sune.app.mediadownloader.drm.DRMBrowser;
import sune.app.mediadownloader.drm.util.DRMUtils.Promise;

public final class Playback {
	
	private final DRMBrowser browser;
	private final CefFrame frame;
	private final int videoId;
	
	public Playback(DRMBrowser browser, CefFrame frame, int videoId) {
		this.browser = browser;
		this.frame = frame;
		this.videoId = videoId;
	}
	
	public Promise.OfVoid play() {
		return JS.Playback.play(browser, frame, videoId);
	}
	
	public Promise.OfVoid pause() {
		return JS.Playback.pause(browser, frame, videoId);
	}
	
	public Promise.OfVoid time(double time, boolean keepPaused) {
		return JS.Playback.time(browser, frame, videoId, time, keepPaused);
	}
	
	public Promise.OfVoid mute() {
		return JS.Playback.mute(browser, frame, videoId);
	}
	
	public Promise.OfVoid unmute() {
		return JS.Playback.unmute(browser, frame, videoId);
	}
	
	public Promise.OfVoid volume(double volume) {
		return JS.Playback.volume(browser, frame, videoId, volume);
	}
}