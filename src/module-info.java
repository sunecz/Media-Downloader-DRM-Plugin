module sune.app.mediadown.drm {
	// Internal modules
	requires java.base;
	requires transitive java.desktop;
	// External modules (Other)
	requires transitive ssdf2;
	requires transitive sune.app.mediadown;
	requires sune.api.process;
	requires transitive sune.util.load;
	// Exports
	exports sune.app.mediadown.drm;
	exports sune.app.mediadown.drm.event;
	exports sune.app.mediadown.drm.util;
}