package sune.app.mediadownloader.drm;

import java.util.UUID;

import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadownloader.drm.util.PlaybackController;
import sune.app.mediadownloader.drm.util.PlaybackData;
import sune.app.mediadownloader.drm.util.PlaybackEventsHandler;
import sune.app.mediadownloader.drm.util.ProcessManager;

public interface DRMContext {
	
	void ready(double duration, int videoID, long frameID);
	boolean hasRecordStarted();
	
	void playbackEventsHandler(PlaybackEventsHandler handler);
	void syncTime(PlaybackData data);
	void syncWait(PlaybackData data);
	void syncResume(PlaybackData data);
	void syncStop(PlaybackData data);
	void playbackPause(PlaybackData data);
	void playbackResume(PlaybackData data);
	void playbackReady(PlaybackData data);
	
	DRMEventRegistry eventRegistry();
	TrackerManager trackerManager();
	DRMBrowserContext browserContext();
	PlaybackController playbackController();
	ProcessManager processManager();
	
	UUID uuid();
	DRMConfiguration configuration();
	String audioDeviceName();
}