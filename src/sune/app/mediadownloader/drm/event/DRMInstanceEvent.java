package sune.app.mediadownloader.drm.event;

import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.IEventType;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMContext;

public final class DRMInstanceEvent implements IEventType {
	
	public static final EventType<DRMInstanceEvent, DRMContext>                  BEGIN  = new EventType<>();
	public static final EventType<DRMInstanceEvent, DRMContext>                  END    = new EventType<>();
	public static final EventType<DRMInstanceEvent, Pair<DRMContext, Exception>> ERROR  = new EventType<>();
	public static final EventType<DRMInstanceEvent, DRMContext>                  PAUSE  = new EventType<>();
	public static final EventType<DRMInstanceEvent, DRMContext>                  RESUME = new EventType<>();
	
	private static final EventType<DRMInstanceEvent, ?>[] VALUES = Utils.array(BEGIN, END, ERROR, PAUSE, RESUME);
	public  static final EventType<DRMInstanceEvent, ?>[] values() {
		return VALUES;
	}
	
	// Forbid anyone to create an instance of this class
	private DRMInstanceEvent() {
	}
}