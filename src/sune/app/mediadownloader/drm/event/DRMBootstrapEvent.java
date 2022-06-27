package sune.app.mediadownloader.drm.event;

import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.IEventType;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMBootstrap;

public final class DRMBootstrapEvent implements IEventType {
	
	public static final EventType<DRMBootstrapEvent, CheckEventContext<DRMBootstrap>>      RESOURCE_CHECK           = new EventType<>();
	public static final EventType<DRMBootstrapEvent, DownloadEventContext<DRMBootstrap>>   RESOURCE_DOWNLOAD_BEGIN  = new EventType<>();
	public static final EventType<DRMBootstrapEvent, DownloadEventContext<DRMBootstrap>>   RESOURCE_DOWNLOAD_UPDATE = new EventType<>();
	public static final EventType<DRMBootstrapEvent, DownloadEventContext<DRMBootstrap>>   RESOURCE_DOWNLOAD_END    = new EventType<>();
	public static final EventType<DRMBootstrapEvent, LibraryEventContext<DRMBootstrap>>    LIBRARY_LOADING          = new EventType<>();
	public static final EventType<DRMBootstrapEvent, LibraryEventContext<DRMBootstrap>>    LIBRARY_LOADED           = new EventType<>();
	public static final EventType<DRMBootstrapEvent, LibrariesEventContext<DRMBootstrap>>  LIBRARIES_ERROR          = new EventType<>();
	public static final EventType<DRMBootstrapEvent, Pair<DRMBootstrap, Exception>>        ERROR                    = new EventType<>();
	
	private static final EventType<DRMBootstrapEvent, ?>[] VALUES = Utils.array
		(
			RESOURCE_CHECK,
			RESOURCE_DOWNLOAD_BEGIN, RESOURCE_DOWNLOAD_UPDATE, RESOURCE_DOWNLOAD_END,
			LIBRARY_LOADING, LIBRARY_LOADED, LIBRARIES_ERROR,
			ERROR
		);
	public  static final EventType<DRMBootstrapEvent, ?>[] values() {
		return VALUES;
	}
	
	// Forbid anyone to create an instance of this class
	private DRMBootstrapEvent() {
	}
}