package sune.app.mediadown.drm;

import java.nio.file.Path;

import sune.app.mediadown.download.DownloadResult;
import sune.app.mediadown.download.MediaDownloadConfiguration;
import sune.app.mediadown.entity.Downloader;
import sune.app.mediadown.media.Media;
import sune.app.mediadown.media.MediaUtils;
import sune.app.mediadown.plugin.PluginBase;
import sune.app.mediadown.plugin.PluginLoaderContext;

public final class DRMDownloader implements Downloader {
	
	private static final PluginBase PLUGIN = PluginLoaderContext.getContext().getInstance();
	
	public static final String TITLE   = PLUGIN.getTitle();
	public static final String VERSION = PLUGIN.getVersion();
	public static final String AUTHOR  = PLUGIN.getAuthor();
	
	// Allow to create an instance when registering the downloader
	DRMDownloader() {
	}
	
	@Override
	public DownloadResult download(Media media, Path destination, MediaDownloadConfiguration configuration)
			throws Exception {
		return new DecryptingDownloader(media, destination, configuration);
	}
	
	@Override
	public boolean isDownloadable(Media media) {
		return media.metadata().isProtected() && MediaUtils.isSegmentedMedia(media);
	}
	
	@Override
	public String title() {
		return TITLE;
	}
	
	@Override
	public String version() {
		return VERSION;
	}
	
	@Override
	public String author() {
		return AUTHOR;
	}
	
	@Override
	public String toString() {
		return TITLE;
	}
}