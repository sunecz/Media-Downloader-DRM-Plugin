package sune.app.mediadownloader.drm;

import java.util.UUID;

import sune.app.mediadown.event.EventRegistry;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadownloader.drm.util.Playback;
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
	
	EventRegistry<EventType> eventRegistry();
	TrackerManager trackerManager();
	DRMBrowserContext browserContext();
	Playback playback();
	ProcessManager processManager();
	DRMCommandFactory commandFactory();
	
	UUID uuid();
	DRMConfiguration configuration();
	String audioDeviceName();
}