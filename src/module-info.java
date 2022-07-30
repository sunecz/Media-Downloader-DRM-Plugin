module sune.app.mediadownloader.drm {
	// Internal modules
	requires java.base;
	requires transitive java.desktop;
	// External modules (Cef)
	requires transitive jcef;
	requires jogl.all;
	// External modules (Proxy)
	requires littleproxy;
	requires littleproxy.mitm;
	requires transitive netty.all;
	// External modules (Logging)
	requires transitive slf4j.api;
	requires log4j;
	// External modules (Other)
	requires transitive ssdf2;
	requires transitive sune.app.mediadown;
	requires sune.api.process;
	requires transitive sune.util.load;
	// Exports
	exports sune.app.mediadownloader.drm;
	exports sune.app.mediadownloader.drm.event;
	exports sune.app.mediadownloader.drm.integration;
	exports sune.app.mediadownloader.drm.phase;
	exports sune.app.mediadownloader.drm.process;
	exports sune.app.mediadownloader.drm.resolver;
	exports sune.app.mediadownloader.drm.tracker;
	exports sune.app.mediadownloader.drm.util;
}