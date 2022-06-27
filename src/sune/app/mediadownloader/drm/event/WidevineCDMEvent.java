package sune.app.mediadownloader.drm.event;

import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.IEventType;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMContext;

public final class WidevineCDMEvent implements IEventType {
	
	public static final EventType<WidevineCDMEvent, DRMContext>                       BEGIN            = new EventType<>();
	public static final EventType<WidevineCDMEvent, DRMContext>                       WAIT_CEF_REQUEST = new EventType<>();
	public static final EventType<WidevineCDMEvent, DRMContext>                       END              = new EventType<>();
	public static final EventType<WidevineCDMEvent, Pair<DRMContext, Exception>>      ERROR            = new EventType<>();
	public static final EventType<WidevineCDMEvent, DRMContext>                       BEGIN_REQUEST    = new EventType<>();
	public static final EventType<WidevineCDMEvent, DRMContext>                       END_REQUEST      = new EventType<>();
	public static final EventType<WidevineCDMEvent, DRMContext>                       BEGIN_DOWNLOAD   = new EventType<>();
	public static final EventType<WidevineCDMEvent, Pair<DRMContext, TrackerManager>> UPDATE_DOWNLOAD  = new EventType<>();
	public static final EventType<WidevineCDMEvent, DRMContext>                       END_DOWNLOAD     = new EventType<>();
	public static final EventType<WidevineCDMEvent, DRMContext>                       BEGIN_EXTRACT    = new EventType<>();
	public static final EventType<WidevineCDMEvent, DRMContext>                       END_EXTRACT      = new EventType<>();
	
	private static final EventType<WidevineCDMEvent, ?>[] VALUES = Utils.array(BEGIN, WAIT_CEF_REQUEST, END, ERROR,
		BEGIN_REQUEST, END_REQUEST, BEGIN_DOWNLOAD, UPDATE_DOWNLOAD, END_DOWNLOAD, BEGIN_EXTRACT, END_EXTRACT);
	public  static final EventType<WidevineCDMEvent, ?>[] values() {
		return VALUES;
	}
	
	// Forbid anyone to create an instance of this class
	private WidevineCDMEvent() {
	}
}