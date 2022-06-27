package sune.app.mediadownloader.drm.util;

import sune.util.ssdf2.SSDCollection;

public final class PlaybackData {
	
	public final double time;
	public final int frame;
	public final double buffered;
	
	public PlaybackData(double time, int frame, double buffered) {
		this.time = time;
		this.frame = frame;
		this.buffered = buffered;
	}
	
	public PlaybackData(SSDCollection json) {
		this(json.getDirectDouble("time"), json.getDirectInt("frame"), json.getDirectDouble("buffered"));
	}
}