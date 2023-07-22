package sune.app.mediadown.drm.event;

import sune.app.mediadown.drm.DRMBootstrap;
import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;

public final class DRMBootstrapEvent implements EventType {
	
	public static final Event<DRMBootstrapEvent, CheckEventContext<DRMBootstrap>>      RESOURCE_CHECK           = new Event<>();
	public static final Event<DRMBootstrapEvent, DownloadEventContext<DRMBootstrap>>   RESOURCE_DOWNLOAD_BEGIN  = new Event<>();
	public static final Event<DRMBootstrapEvent, DownloadEventContext<DRMBootstrap>>   RESOURCE_DOWNLOAD_UPDATE = new Event<>();
	public static final Event<DRMBootstrapEvent, DownloadEventContext<DRMBootstrap>>   RESOURCE_DOWNLOAD_END    = new Event<>();
	public static final Event<DRMBootstrapEvent, Pair<DRMBootstrap, Exception>>        ERROR                    = new Event<>();
	
	private static Event<DRMBootstrapEvent, ?>[] values;
	
	// Forbid anyone to create an instance of this class
	private DRMBootstrapEvent() {
	}
	
	public static final Event<DRMBootstrapEvent, ?>[] values() {
		if(values == null) {
			values = Utils.array(
				RESOURCE_CHECK,
				RESOURCE_DOWNLOAD_BEGIN, RESOURCE_DOWNLOAD_UPDATE, RESOURCE_DOWNLOAD_END,
				ERROR
			);
		}
		
		return values;
	}
}