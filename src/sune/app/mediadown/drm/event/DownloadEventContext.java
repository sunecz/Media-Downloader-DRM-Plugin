package sune.app.mediadown.drm.event;

import java.net.URI;
import java.nio.file.Path;

import sune.app.mediadown.event.tracker.DownloadTracker;

public class DownloadEventContext<C> extends EventContext<C> {
	
	private final URI uri;
	private final Path path;
	private final DownloadTracker tracker;

	public DownloadEventContext(C context, URI uri, Path path, DownloadTracker tracker) {
		super(context);
		this.uri = uri;
		this.path = path;
		this.tracker = tracker;
	}
	
	public URI uri() {
		return uri;
	}
	
	public Path path() {
		return path;
	}
	
	public DownloadTracker tracker() {
		return tracker;
	}
}