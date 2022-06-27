package sune.app.mediadownloader.drm.resolver;

import java.awt.Dimension;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import org.cef.browser.CefFrame;

import sune.app.mediadown.media.MediaQuality;
import sune.app.mediadownloader.drm.DRMBrowser;
import sune.app.mediadownloader.drm.DRMContext;
import sune.app.mediadownloader.drm.DRMResolver;
import sune.app.mediadownloader.drm.util.DRMUtils;
import sune.app.mediadownloader.drm.util.DRMUtils.JSRequest;
import sune.app.mediadownloader.drm.util.DRMUtils.Point2D;
import sune.app.mediadownloader.drm.util.PlaybackData;
import sune.util.ssdf2.SSDCollection;

public abstract class SimpleDRMResolver implements DRMResolver {
	
	protected final DRMContext context;
	protected final String url;
	protected final Path output;
	protected final MediaQuality quality;
	
	private int videoWidth;
	private int videoHeight;
	private double duration;
	private int vid;
	
	public SimpleDRMResolver(DRMContext context, String url, Path output, MediaQuality quality) {
		this.context = context;
		this.url = url;
		this.output = output;
		this.quality = quality;
	}
	
	protected void includeMainScript(CefFrame frame) {
		DRMUtils.executeJSFile(frame, "/resources/drm/video-grabber.js");
	}
	
	protected void includeHelperScripts(CefFrame frame) {
		DRMUtils.executeJSFile(frame, "/resources/drm/helper.js");
		String jsCSSCode = ""
				+ "let style = document.createElement('style');"
				+ "style.textContent = 'video::-webkit-media-controls{display:none!important;-webkit-appearance:none!important;}';"
				+ "document.head.appendChild(style);";
		frame.executeJavaScript(jsCSSCode, null, 0);
	}
	
	/**
	 * <pre>
	 * +--------------------------------------------------------------------------------+
	 * |                              Video playback events                             |
	 * +-------------+------------------------------------------------------------------+
	 * | NAME        | DESCRIPTION                                                      |
	 * +-------------+------------------------------------------------------------------+
	 * | metadata    | Video metadata are done loading                                  |
	 * | fullscreen  | Video is playing in fullscreen mode, ready to be recorded        |
	 * | waiting     | Video is either paused or buffering (browser native buffering)   |
	 * | playing     | Video is playing, either after buffering or after the play event |
	 * | update      | Video playback was updated (timeupdate, etc.)                    |
	 * | canplay     | At least a part of video can be played                           |
	 * | ended       | Video playback ended                                             |
	 * | bufferPause | Video paused to preemptively buffer                              |
	 * | bufferPlay  | Video is preemptively buffered enough and can be played          |
	 * +-------------+------------------------------------------------------------------+
	 * </pre>
	 */
	@Override
	public void onRequest(DRMBrowser browser, CefFrame frame, String requestName, SSDCollection json, String request) {
		switch(requestName) {
			case "update":
				context.syncTime(new PlaybackData(json));
				break;
			case "waiting":
				context.syncWait(new PlaybackData(json));
				break;
			case "canplay":
				context.playbackReady(new PlaybackData(json));
				break;
			case "playing":
				context.syncResume(new PlaybackData(json));
				break;
			case "ended":
				context.syncStop(new PlaybackData(json));
				break;
			case "bufferPlay":
				context.playbackResume(new PlaybackData(json));
				break;
			case "bufferPause":
				context.playbackPause(new PlaybackData(json));
				break;
			case "metadata":
				videoWidth = (int) json.getDirectDouble("width");
				videoHeight = (int) json.getDirectDouble("height");
				duration = json.getDirectDouble("duration");
				vid = Integer.valueOf(json.getDirectInt("id"));
				String buttonID = "sune-button-" + vid;
				String jsCode = ""
						+ "let button = document.getElementById('" + buttonID + "');"
						+ "let ok = false, click = (e) => { ok = true; };"
						+ "button.addEventListener('click', click, false);"
						+ "let intr = setInterval(() => {"
						+ "    if(ok) { clearInterval(intr); return; }"
						+ "    const rect = button.getBoundingClientRect();"
						+ "    const pos = currentFrameAbsolutePosition();"
						+ "    rect.x += pos.x;"
						+ "    rect.y += pos.y;"
						+ "    ret(0, rect);"
						+ "}, 100);";
				browser.addJSRequest(new JSRequest("bbox-button", jsCode, (result) -> {
					SSDCollection bbox = (SSDCollection) result;
					Point2D center = DRMUtils.getCenter(DRMUtils.getBBox(bbox));
					browser.accessor().click(center.x, center.y);
				}).send(frame));
				break;
			case "fullscreen":
				boolean isFullscreen = json.getDirectBoolean("value");
				if(!context.hasRecordStarted() && isFullscreen && videoWidth >= 0 && videoHeight >= 0) {
					long frameID = frame.getIdentifier();
					SwingUtilities.invokeLater(() -> {
						browser.getContentPane().setPreferredSize(new Dimension(videoWidth, videoHeight));
						browser.pack();
						context.ready(duration, vid, frameID);
					});
				}
				break;
		}
	}
}