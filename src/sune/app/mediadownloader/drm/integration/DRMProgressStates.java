package sune.app.mediadownloader.drm.integration;

public final class DRMProgressStates {
	
	public static final String INSTANCE     = "tr(plugin:drm, states.instance)";
	public static final String WIDEVINE     = "tr(plugin:drm, states.widevine)";
	public static final String ANALYZE      = "tr(plugin:drm, states.analyze)";
	public static final String RECORD       = "tr(plugin:drm, states.record)";
	public static final String POST_PROCESS = "tr(plugin:drm, states.post_process)";
	
	private DRMProgressStates() {
	}
}