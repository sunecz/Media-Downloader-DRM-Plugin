package sune.app.mediadownloader.drm.integration;

import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.pipeline.PipelineTaskRegistry;
import sune.app.mediadown.plugin.Plugin;
import sune.app.mediadown.plugin.PluginBase;
import sune.app.mediadownloader.drm.DRMInitializer;
import sune.app.mediadownloader.drm.DRMInstance;

@Plugin(name          = "drm",
		title         = "plugin.drm.title",
		version       = "0011",
		author        = "Sune",
		updateBaseURL = "https://app.sune.tech/mediadown/dat/plugin/0002/plugin/drm/",
		updatable     = true,
		url           = "https://suneweb.net/project/media-downloader/",
		icon          = "resources/drm/icon/drm.png",
		moduleName    = "sune.app.mediadownloader.drm")
public final class DRMPlugin extends PluginBase {
	
	private String translatedTitle;
	
	@Override
	public void init() throws Exception {
		translatedTitle = MediaDownloader.translation().getSingle(super.getTitle());
		PipelineTaskRegistry.register("ProtectedMediaPipelineTask", ProtectedMediaPipelineTask.class);
		IntegrationUtils.setPluginContext(getContext());
		DRMInitializer.initializeClasses();
	}
	
	@Override
	public void dispose() throws Exception {
		// Stop and dispose all existing instances
		for(DRMInstance instance : DRMInstance.instances()) {
			instance.context().browserContext().close();
			instance.stop();
		}
	}
	
	@Override
	public String getTitle() {
		return translatedTitle;
	}
}