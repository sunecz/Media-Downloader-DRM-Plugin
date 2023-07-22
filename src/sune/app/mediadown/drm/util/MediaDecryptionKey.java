package sune.app.mediadown.drm.util;

import java.util.Objects;

public final class MediaDecryptionKey {
	
	private final String kid;
	private final String key;
	
	public MediaDecryptionKey(String kid, String key) {
		this.kid = Objects.requireNonNull(kid);
		this.key = Objects.requireNonNull(key);
	}
	
	public String kid() {
		return kid;
	}
	
	public String key() {
		return key;
	}
}