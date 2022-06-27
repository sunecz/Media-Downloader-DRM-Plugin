package sune.app.mediadownloader.drm.util;

public interface PlaybackEventsHandler {
	
	void updated(PlaybackData data);
	void waiting(PlaybackData data);
	void resumed(PlaybackData data);
	void ended(PlaybackData data);
}