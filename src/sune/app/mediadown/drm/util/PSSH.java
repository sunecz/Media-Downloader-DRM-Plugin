package sune.app.mediadown.drm.util;

public final class PSSH {
	
	private final String content;
	private final String keyId;
	
	public PSSH(String content, String keyId) {
		this.content = content;
		this.keyId = keyId;
	}
	
	public String content() {
		return content;
	}
	
	public String keyId() {
		return keyId;
	}
}