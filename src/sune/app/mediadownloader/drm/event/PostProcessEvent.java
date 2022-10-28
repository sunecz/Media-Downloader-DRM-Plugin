package sune.app.mediadownloader.drm.event;

import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMContext;

public final class PostProcessEvent implements EventType {
	
	public static final Event<PostProcessEvent, DRMContext>                       BEGIN  = new Event<>();
	public static final Event<PostProcessEvent, Pair<DRMContext, TrackerManager>> UPDATE = new Event<>();
	public static final Event<PostProcessEvent, DRMContext>                       END    = new Event<>();
	public static final Event<PostProcessEvent, Pair<DRMContext, Exception>>      ERROR  = new Event<>();
	public static final Event<PostProcessEvent, DRMContext>                       PAUSE  = new Event<>();
	public static final Event<PostProcessEvent, DRMContext>                       RESUME = new Event<>();
	
	private static Event<PostProcessEvent, ?>[] values;
	
	// Forbid anyone to create an instance of this class
	private PostProcessEvent() {
	}
	
	public static final Event<PostProcessEvent, ?>[] values() {
		if(values == null) {
			values = Utils.array(BEGIN, UPDATE, END, ERROR, PAUSE, RESUME);
		}
		
		return values;
	}
}