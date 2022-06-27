package sune.app.mediadownloader.drm.event;

import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.IEventType;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMContext;

public final class RecordEvent implements IEventType {
	
	public static final EventType<RecordEvent, DRMContext>                       BEGIN  = new EventType<>();
	public static final EventType<RecordEvent, Pair<DRMContext, TrackerManager>> UPDATE = new EventType<>();
	public static final EventType<RecordEvent, DRMContext>                       END    = new EventType<>();
	public static final EventType<RecordEvent, Pair<DRMContext, Exception>>      ERROR  = new EventType<>();
	public static final EventType<RecordEvent, DRMContext>                       PAUSE  = new EventType<>();
	public static final EventType<RecordEvent, DRMContext>                       RESUME = new EventType<>();
	
	private static final EventType<RecordEvent, ?>[] VALUES = Utils.array(BEGIN, UPDATE, END, ERROR, PAUSE, RESUME);
	public  static final EventType<RecordEvent, ?>[] values() {
		return VALUES;
	}
	
	// Forbid anyone to create an instance of this class
	private RecordEvent() {
	}
}