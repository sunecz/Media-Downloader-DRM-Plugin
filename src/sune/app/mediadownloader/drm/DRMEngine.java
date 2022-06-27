package sune.app.mediadownloader.drm;

import java.nio.file.Path;

import sune.app.mediadown.media.MediaQuality;

public interface DRMEngine {
	
	boolean isCompatibleURL(String url);
	DRMResolver createResolver(DRMContext context, String url, Path output, MediaQuality quality);
}