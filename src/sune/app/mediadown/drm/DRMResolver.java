package sune.app.mediadown.drm;

import sune.app.mediadown.media.Media;
import sune.app.mediadown.net.Web.Request;

public interface DRMResolver {
	
	Request createRequest(Media media, byte[] licenseRequest);
}