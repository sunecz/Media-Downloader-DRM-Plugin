package sune.app.mediadown.drm;

import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.entity.Downloaders;
import sune.app.mediadown.plugin.Plugin;
import sune.app.mediadown.plugin.PluginBase;

@Plugin(name          = "drm",
		title         = "plugin.drm.title",
		version       = "00.02.09-0001",
		author        = "Sune",
		updateBaseURL = "https://app.sune.tech/mediadown/dat/plugin/0002/plugin/drm/",
		updatable     = true,
		url           = "https://suneweb.net/project/media-downloader/",
		icon          = "resources/drm/icon/drm.png",
		moduleName    = "sune.app.mediadown.drm")
public final class DRMPlugin extends PluginBase {
	
	private String translatedTitle;
	
	@Override
	public void init() throws Exception {
		translatedTitle = MediaDownloader.translation().getSingle(super.getTitle());
		Downloaders.add("drm", DRMDownloader.class);
	}
	
	@Override
	public void dispose() throws Exception {
		// Do nothing
	}
	
	@Override
	public String getTitle() {
		return translatedTitle;
	}
}