package sune.app.mediadownloader.drm.util;

import sune.app.mediadown.util.JSON.JSONCollection;

public final class PlaybackData {
	
	public final double time;
	public final int frame;
	public final double buffered;
	public final long now;
	
	public PlaybackData(double time, int frame, double buffered, long now) {
		this.time = time;
		this.frame = frame;
		this.buffered = buffered;
		this.now = now;
	}
	
	public PlaybackData(JSONCollection json) {
		this(json.getDouble("time"), json.getInt("frame"), json.getDouble("buffered"), json.getLong("now"));
	}
}