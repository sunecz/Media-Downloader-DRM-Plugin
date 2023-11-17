package sune.app.mediadown.drm;

public final class DRMProgressStates {
	
	public static final String EXTRACT_PSSH  = "tr(plugin:drm, states.extract_pssh)";
	public static final String OBTAIN_KEYS   = "tr(plugin:drm, states.obtain_keys)";
	public static final String DECRYPT_VIDEO = "tr(plugin:drm, states.decrypt_video)";
	public static final String DECRYPT_AUDIO = "tr(plugin:drm, states.decrypt_audio)";
	
	// Forbid anyone to create an instance of this class
	private DRMProgressStates() {
	}
}