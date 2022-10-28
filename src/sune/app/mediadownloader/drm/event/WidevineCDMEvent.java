package sune.app.mediadownloader.drm.event;

import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMContext;

public final class WidevineCDMEvent implements EventType {
	
	public static final Event<WidevineCDMEvent, DRMContext>                       BEGIN            = new Event<>();
	public static final Event<WidevineCDMEvent, DRMContext>                       END              = new Event<>();
	public static final Event<WidevineCDMEvent, Pair<DRMContext, Exception>>      ERROR            = new Event<>();
	public static final Event<WidevineCDMEvent, DRMContext>                       BEGIN_REQUEST    = new Event<>();
	public static final Event<WidevineCDMEvent, DRMContext>                       END_REQUEST      = new Event<>();
	public static final Event<WidevineCDMEvent, DRMContext>                       WAIT_CEF_REQUEST = new Event<>();
	public static final Event<WidevineCDMEvent, DRMContext>                       BEGIN_DOWNLOAD   = new Event<>();
	public static final Event<WidevineCDMEvent, Pair<DRMContext, TrackerManager>> UPDATE_DOWNLOAD  = new Event<>();
	public static final Event<WidevineCDMEvent, DRMContext>                       END_DOWNLOAD     = new Event<>();
	public static final Event<WidevineCDMEvent, DRMContext>                       BEGIN_EXTRACT    = new Event<>();
	public static final Event<WidevineCDMEvent, DRMContext>                       END_EXTRACT      = new Event<>();
	
	private static Event<WidevineCDMEvent, ?>[] values;
	
	// Forbid anyone to create an instance of this class
	private WidevineCDMEvent() {
	}
	
	public static final Event<WidevineCDMEvent, ?>[] values() {
		if(values == null) {
			values = Utils.array(
				BEGIN, END, ERROR,
				BEGIN_REQUEST, END_REQUEST, WAIT_CEF_REQUEST,
				BEGIN_DOWNLOAD, UPDATE_DOWNLOAD, END_DOWNLOAD,
				BEGIN_EXTRACT, END_EXTRACT
			);
		}
		
		return values;
	}
}