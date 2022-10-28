package sune.app.mediadownloader.drm.event;

import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMContext;

public final class RecordEvent implements EventType {
	
	public static final Event<RecordEvent, DRMContext>                       BEGIN  = new Event<>();
	public static final Event<RecordEvent, Pair<DRMContext, TrackerManager>> UPDATE = new Event<>();
	public static final Event<RecordEvent, DRMContext>                       END    = new Event<>();
	public static final Event<RecordEvent, Pair<DRMContext, Exception>>      ERROR  = new Event<>();
	public static final Event<RecordEvent, DRMContext>                       PAUSE  = new Event<>();
	public static final Event<RecordEvent, DRMContext>                       RESUME = new Event<>();
	
	private static Event<RecordEvent, ?>[] values;
	
	// Forbid anyone to create an instance of this class
	private RecordEvent() {
	}
	
	public static final Event<RecordEvent, ?>[] values() {
		if(values == null) {
			values = Utils.array(BEGIN, UPDATE, END, ERROR, PAUSE, RESUME);
		}
		
		return values;
	}
}