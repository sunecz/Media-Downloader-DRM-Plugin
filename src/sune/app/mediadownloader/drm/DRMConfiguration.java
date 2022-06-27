package sune.app.mediadownloader.drm;

import java.nio.file.Path;

import sune.app.mediadown.media.MediaQuality;

public final class DRMConfiguration {
	
	private final Path output;
	private final MediaQuality mediaQuality;
	private final boolean detectFPS;
	private final double analyzeDuration;
	
	private DRMConfiguration(Path output, MediaQuality mediaQuality, boolean detectFPS, double analyzeDuration) {
		this.output = output;
		this.mediaQuality = mediaQuality;
		this.detectFPS = detectFPS;
		this.analyzeDuration = analyzeDuration;
	}
	
	public Path output() {
		return output;
	}
	
	public MediaQuality mediaQuality() {
		return mediaQuality;
	}
	
	public boolean detectFPS() {
		return detectFPS;
	}
	
	public double analyzeDuration() {
		return analyzeDuration;
	}
	
	public static final class Builder {
		
		private static final double DEFAULT_ANALYZE_DURATION = 10.0;
		
		private Path output;
		private MediaQuality mediaQuality;
		private boolean detectFPS;
		private double analyzeDuration;
		
		public Builder() {
			output = null;
			mediaQuality = MediaQuality.UNKNOWN;
			detectFPS = true;
			analyzeDuration = DEFAULT_ANALYZE_DURATION;
		}
		
		public DRMConfiguration build() {
			if(output == null)
				throw new IllegalArgumentException("Output cannot be null");
			if(mediaQuality == null)
				throw new IllegalArgumentException("Media quality cannot be null");
			if(detectFPS && analyzeDuration <= 0.0)
				throw new IllegalArgumentException("Analyze duration must be positive.");
			return new DRMConfiguration(output, mediaQuality, detectFPS, analyzeDuration);
		}
		
		public Builder output(Path output) {
			this.output = output;
			return this;
		}
		
		public Builder mediaQuality(MediaQuality mediaQuality) {
			this.mediaQuality = mediaQuality;
			return this;
		}
		
		public Builder detectFPS(boolean detectFPS) {
			this.detectFPS = detectFPS;
			return this;
		}
		
		public Builder analyzeDuration(double analyzeDuration) {
			this.analyzeDuration = analyzeDuration;
			return this;
		}
		
		public Path output() {
			return output;
		}
		
		public MediaQuality mediaQuality() {
			return mediaQuality;
		}
		
		public boolean detectFPS() {
			return detectFPS;
		}
		
		public double analyzeDuration() {
			return analyzeDuration;
		}
	}
}