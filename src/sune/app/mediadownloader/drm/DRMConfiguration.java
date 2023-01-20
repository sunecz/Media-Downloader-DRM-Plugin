package sune.app.mediadownloader.drm;

import java.nio.file.Path;
import java.util.Objects;

import sune.app.mediadown.media.Media;
import sune.app.mediadownloader.drm.util.Quality;

public final class DRMConfiguration {
	
	private final Path output;
	private final Media media;
	private final boolean detectFrameRate;
	private final double analyzeDuration;
	private final Quality quality;
	private final String captureAudioDeviceName;
	private final String renderAudioDeviceName;
	private final boolean allowVirtualAudioDevice;
	private final boolean recordUseDisplayRefreshRate;
	private final double recordFrameRate;
	private final boolean outputUseMediaFrameRate;
	private final double outputFrameRate;
	private final boolean keepRecordFile;
	
	private DRMConfiguration(Path output, Media media, boolean detectFrameRate, double analyzeDuration,
			Quality quality, String captureAudioDeviceName, String renderAudioDeviceName,
			boolean allowVirtualAudioDevice, boolean recordUseDisplayRefreshRate, double recordFrameRate,
			boolean outputUseMediaFrameRate, double outputFrameRate, boolean keepRecordFile) {
		this.output = output;
		this.media = media;
		this.detectFrameRate = detectFrameRate;
		this.analyzeDuration = analyzeDuration;
		this.quality = quality;
		this.captureAudioDeviceName = captureAudioDeviceName;
		this.renderAudioDeviceName = renderAudioDeviceName;
		this.allowVirtualAudioDevice = allowVirtualAudioDevice;
		this.recordUseDisplayRefreshRate = recordUseDisplayRefreshRate;
		this.recordFrameRate = recordFrameRate;
		this.outputUseMediaFrameRate = outputUseMediaFrameRate;
		this.outputFrameRate = outputFrameRate;
		this.keepRecordFile = keepRecordFile;
	}
	
	public Path output() {
		return output;
	}
	
	public Media media() {
		return media;
	}
	
	public boolean detectFrameRate() {
		return detectFrameRate;
	}
	
	public double analyzeDuration() {
		return analyzeDuration;
	}
	
	public Quality quality() {
		return quality;
	}
	
	public String captureAudioDeviceName() {
		return captureAudioDeviceName;
	}
	
	public String renderAudioDeviceName() {
		return renderAudioDeviceName;
	}
	
	public boolean allowVirtualAudioDevice() {
		return allowVirtualAudioDevice;
	}
	
	public boolean recordUseDisplayRefreshRate() {
		return recordUseDisplayRefreshRate;
	}
	
	public double recordFrameRate() {
		return recordFrameRate;
	}
	
	public boolean outputUseMediaFrameRate() {
		return outputUseMediaFrameRate;
	}
	
	public double outputFrameRate() {
		return outputFrameRate;
	}
	
	public boolean keepRecordFile() {
		return keepRecordFile;
	}
	
	public static final class Builder {
		
		private static final double DEFAULT_ANALYZE_DURATION = 10.0;
		private static final double DEFAULT_RECORD_FRAME_RATE = 24.0;
		private static final double DEFAULT_OUTPUT_FRAME_RATE = 24.0;
		
		private Path output;
		private Media media;
		private boolean detectFrameRate;
		private double analyzeDuration;
		private Quality quality;
		private String captureAudioDeviceName;
		private String renderAudioDeviceName;
		private boolean allowVirtualAudioDevice;
		private boolean recordUseDisplayRefreshRate;
		private double recordFrameRate;
		private boolean outputUseMediaFrameRate;
		private double outputFrameRate;
		private boolean keepRecordFile;
		
		public Builder() {
			output = null;
			media = null;
			detectFrameRate = false;
			analyzeDuration = DEFAULT_ANALYZE_DURATION;
			quality = Quality.LOSSLESS;
			captureAudioDeviceName = "auto";
			renderAudioDeviceName = "auto";
			allowVirtualAudioDevice = false;
			recordUseDisplayRefreshRate = true;
			recordFrameRate = DEFAULT_RECORD_FRAME_RATE;
			outputUseMediaFrameRate = true;
			outputFrameRate = DEFAULT_OUTPUT_FRAME_RATE;
			keepRecordFile = false;
		}
		
		public DRMConfiguration build() {
			if(detectFrameRate && analyzeDuration <= 0.0) {
				throw new IllegalArgumentException("Analyze duration must be > 0.0.");
			}
			
			if(!recordUseDisplayRefreshRate && recordFrameRate <= 0.0) {
				throw new IllegalArgumentException("Record frame rate must be > 0.0.");
			}
			
			if(!outputUseMediaFrameRate && outputFrameRate <= 0.0) {
				throw new IllegalArgumentException("Output frame rate must be > 0.0.");
			}
			
			return new DRMConfiguration(Objects.requireNonNull(output), Objects.requireNonNull(media),
				detectFrameRate, analyzeDuration, quality, captureAudioDeviceName, renderAudioDeviceName,
				allowVirtualAudioDevice, recordUseDisplayRefreshRate, recordFrameRate, outputUseMediaFrameRate,
				outputFrameRate, keepRecordFile);
		}
		
		public Builder output(Path output) {
			this.output = Objects.requireNonNull(output);
			return this;
		}
		
		public Builder media(Media media) {
			this.media = Objects.requireNonNull(media);
			return this;
		}
		
		public Builder detectFrameRate(boolean detectFrameRate) {
			this.detectFrameRate = detectFrameRate;
			return this;
		}
		
		public Builder analyzeDuration(double analyzeDuration) {
			this.analyzeDuration = analyzeDuration;
			return this;
		}
		
		public Builder quality(Quality quality) {
			this.quality = Objects.requireNonNull(quality);
			return this;
		}
		
		public Builder captureAudioDeviceName(String captureAudioDeviceName) {
			this.captureAudioDeviceName = captureAudioDeviceName;
			return this;
		}
		
		public Builder autoCaptureAudioDeviceName() {
			return captureAudioDeviceName("auto");
		}
		
		public Builder renderAudioDeviceName(String renderAudioDeviceName) {
			this.renderAudioDeviceName = renderAudioDeviceName;
			return this;
		}
		
		public Builder autoRenderAudioDeviceName() {
			return renderAudioDeviceName("auto");
		}
		
		public Builder allowVirtualAudioDevice(boolean allowVirtualAudioDevice) {
			this.allowVirtualAudioDevice = allowVirtualAudioDevice;
			return this;
		}
		
		public Builder recordUseDisplayRefreshRate(boolean recordUseDisplayRefreshRate) {
			this.recordUseDisplayRefreshRate = recordUseDisplayRefreshRate;
			return this;
		}
		
		public Builder recordFrameRate(double recordFrameRate) {
			this.recordFrameRate = recordFrameRate;
			return this;
		}
		
		public Builder outputUseMediaFrameRate(boolean outputUseMediaFrameRate) {
			this.outputUseMediaFrameRate = outputUseMediaFrameRate;
			return this;
		}
		
		public Builder outputFrameRate(double outputFrameRate) {
			this.outputFrameRate = outputFrameRate;
			return this;
		}
		
		public Builder keepRecordFile(boolean keepRecordFile) {
			this.keepRecordFile = keepRecordFile;
			return this;
		}
		
		public Path output() {
			return output;
		}
		
		public Media media() {
			return media;
		}
		
		public boolean detectFrameRate() {
			return detectFrameRate;
		}
		
		public double analyzeDuration() {
			return analyzeDuration;
		}
		
		public Quality quality() {
			return quality;
		}
		
		public String captureAudioDeviceName() {
			return captureAudioDeviceName;
		}
		
		public String renderAudioDeviceName() {
			return renderAudioDeviceName;
		}
		
		public boolean allowVirtualAudioDevice() {
			return allowVirtualAudioDevice;
		}
		
		public boolean recordUseDisplayRefreshRate() {
			return recordUseDisplayRefreshRate;
		}
		
		public double recordFrameRate() {
			return recordFrameRate;
		}
		
		public boolean outputUseMediaFrameRate() {
			return outputUseMediaFrameRate;
		}
		
		public double outputFrameRate() {
			return outputFrameRate;
		}
		
		public boolean keepRecordFile() {
			return keepRecordFile;
		}
	}
}