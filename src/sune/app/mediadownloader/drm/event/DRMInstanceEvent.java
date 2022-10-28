package sune.app.mediadownloader.drm.event;

import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMContext;

public final class DRMInstanceEvent implements EventType {
	
	public static final Event<DRMInstanceEvent, DRMContext>                  BEGIN  = new Event<>();
	public static final Event<DRMInstanceEvent, DRMContext>                  END    = new Event<>();
	public static final Event<DRMInstanceEvent, Pair<DRMContext, Exception>> ERROR  = new Event<>();
	public static final Event<DRMInstanceEvent, DRMContext>                  PAUSE  = new Event<>();
	public static final Event<DRMInstanceEvent, DRMContext>                  RESUME = new Event<>();
	
	private static Event<DRMInstanceEvent, ?>[] values;
	
	// Forbid anyone to create an instance of this class
	private DRMInstanceEvent() {
	}
	
	public static final Event<DRMInstanceEvent, ?>[] values() {
		if(values == null) {
			values = Utils.array(BEGIN, END, ERROR, PAUSE, RESUME);
		}
		
		return values;
	}
}