package sune.app.mediadown.drm.event;

import sune.app.mediadown.HasTaskState;
import sune.app.mediadown.event.EventBindable;
import sune.app.mediadown.event.tracker.Trackable;

public interface DecryptionContext extends EventBindable<DecryptionEvent>, HasTaskState, Trackable {
	
	Exception exception();
}