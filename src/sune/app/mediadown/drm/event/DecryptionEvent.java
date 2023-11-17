package sune.app.mediadown.drm.event;

import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.util.Utils;

public final class DecryptionEvent implements EventType {
	
	public static final Event<DecryptionEvent, DecryptionContext> BEGIN  = new Event<>();
	public static final Event<DecryptionEvent, DecryptionContext> UPDATE = new Event<>();
	public static final Event<DecryptionEvent, DecryptionContext> END    = new Event<>();
	public static final Event<DecryptionEvent, DecryptionContext> ERROR  = new Event<>();
	public static final Event<DecryptionEvent, DecryptionContext> PAUSE  = new Event<>();
	public static final Event<DecryptionEvent, DecryptionContext> RESUME = new Event<>();
	
	private static Event<DecryptionEvent, ?>[] values;
	
	// Forbid anyone to create an instance of this class
	private DecryptionEvent() {
	}
	
	public static final Event<DecryptionEvent, ?>[] values() {
		if(values == null) {
			values = Utils.array(BEGIN, UPDATE, END, ERROR, PAUSE, RESUME);
		}
		
		return values;
	}
}