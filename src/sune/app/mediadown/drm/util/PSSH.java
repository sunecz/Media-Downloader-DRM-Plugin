package sune.app.mediadown.drm.util;

public final class PSSH {
	
	private final String video;
	private final String audio;
	
	public PSSH(String video, String audio) {
		this.video = video;
		this.audio = audio;
	}
	
	public String video() {
		return video;
	}
	
	public String audio() {
		return audio;
	}
}