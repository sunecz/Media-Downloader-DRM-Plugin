package sune.app.mediadown.drm;

import java.net.URI;

public interface DRMEngine {
	
	boolean isCompatibleURI(URI uri);
	DRMResolver createResolver();
}