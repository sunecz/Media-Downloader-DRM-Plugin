package sune.app.mediadownloader.drm.event;

import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMContext;

public final class AnalyzeEvent implements EventType {
	
	public static final Event<AnalyzeEvent, DRMContext>                       BEGIN  = new Event<>();
	public static final Event<AnalyzeEvent, Pair<DRMContext, TrackerManager>> UPDATE = new Event<>();
	public static final Event<AnalyzeEvent, DRMContext>                       END    = new Event<>();
	public static final Event<AnalyzeEvent, Pair<DRMContext, Exception>>      ERROR  = new Event<>();
	public static final Event<AnalyzeEvent, DRMContext>                       PAUSE  = new Event<>();
	public static final Event<AnalyzeEvent, DRMContext>                       RESUME = new Event<>();
	
	private static Event<AnalyzeEvent, ?>[] values;
	
	// Forbid anyone to create an instance of this class
	private AnalyzeEvent() {
	}
	
	public static final Event<AnalyzeEvent, ?>[] values() {
		if(values == null) {
			values = Utils.array(BEGIN, UPDATE, END, ERROR, PAUSE, RESUME);
		}
		
		return values;
	}
}