package sune.app.mediadownloader.drm.event;

import java.net.URL;
import java.nio.file.Path;

import sune.app.mediadown.event.tracker.DownloadTracker;

public class DownloadEventContext<C> extends EventContext<C> {
	
	private final URL url;
	private final Path path;
	private final DownloadTracker tracker;

	public DownloadEventContext(C context, URL url, Path path, DownloadTracker tracker) {
		super(context);
		this.url = url;
		this.path = path;
		this.tracker = tracker;
	}
	
	public URL url() {
		return url;
	}
	
	public Path path() {
		return path;
	}
	
	public DownloadTracker tracker() {
		return tracker;
	}
}