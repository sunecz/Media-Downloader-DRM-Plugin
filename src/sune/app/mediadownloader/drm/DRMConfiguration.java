package sune.app.mediadownloader.drm;

import java.nio.file.Path;
import java.util.Objects;

import sune.app.mediadown.media.Media;

public final class DRMConfiguration {
	
	private final Path output;
	private final Media media;
	private final boolean detectFPS;
	private final double analyzeDuration;
	
	private DRMConfiguration(Path output, Media media, boolean detectFPS, double analyzeDuration) {
		this.output = output;
		this.media = media;
		this.detectFPS = detectFPS;
		this.analyzeDuration = analyzeDuration;
	}
	
	public Path output() {
		return output;
	}
	
	public Media media() {
		return media;
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
		private Media media;
		private boolean detectFPS;
		private double analyzeDuration;
		
		public Builder() {
			output = null;
			media = null;
			detectFPS = false;
			analyzeDuration = DEFAULT_ANALYZE_DURATION;
		}
		
		public DRMConfiguration build() {
			if(detectFPS && analyzeDuration <= 0.0)
				throw new IllegalArgumentException("Analyze duration must be > 0.0.");
			return new DRMConfiguration(Objects.requireNonNull(output), Objects.requireNonNull(media),
					detectFPS, analyzeDuration);
		}
		
		public Builder output(Path output) {
			this.output = Objects.requireNonNull(output);
			return this;
		}
		
		public Builder media(Media media) {
			this.media = Objects.requireNonNull(media);
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
		
		public Media media() {
			return media;
		}
		
		public boolean detectFPS() {
			return detectFPS;
		}
		
		public double analyzeDuration() {
			return analyzeDuration;
		}
	}
}