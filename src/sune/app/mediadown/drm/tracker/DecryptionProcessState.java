package sune.app.mediadown.drm.tracker;

import sune.app.mediadown.drm.DRMProgressStates;

public enum DecryptionProcessState {
	
	NONE(null),
	EXTRACT_PSSH(DRMProgressStates.EXTRACT_PSSH),
	OBTAIN_KEYS(DRMProgressStates.OBTAIN_KEYS),
	DECRYPT_VIDEO(DRMProgressStates.DECRYPT_VIDEO),
	DECRYPT_AUDIO(DRMProgressStates.DECRYPT_AUDIO);
	
	private final String title;
	
	private DecryptionProcessState(String title) {
		this.title = title;
	}
	
	public String title() {
		return title;
	}
}