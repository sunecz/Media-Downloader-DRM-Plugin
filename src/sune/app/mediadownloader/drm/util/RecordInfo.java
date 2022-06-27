package sune.app.mediadownloader.drm.util;

import java.nio.file.Path;
import java.util.List;

public final class RecordInfo {
	
	private final Path path;
	private final List<Cut.OfDouble> cuts;
	private final double frameRate;
	private final int sampleRate;
	private final double audioOffset;
	private final double startCutOff;
	private final double endCutOff;
	
	public RecordInfo(Path path, List<Cut.OfDouble> cuts, double frameRate, int sampleRate, double audioOffset,
			double startCutOff, double endCutOff) {
		this.path = path;
		this.cuts = cuts;
		this.frameRate = frameRate;
		this.sampleRate = sampleRate;
		this.audioOffset = audioOffset;
		this.startCutOff = startCutOff;
		this.endCutOff = endCutOff;
	}
	
	public Path path() {
		return path;
	}
	
	public List<Cut.OfDouble> cuts() {
		return cuts;
	}
	
	public double frameRate() {
		return frameRate;
	}
	
	public int sampleRate() {
		return sampleRate;
	}
	
	public double audioOffset() {
		return audioOffset;
	}
	
	public double startCutOff() {
		return startCutOff;
	}
	
	public double endCutOff() {
		return endCutOff;
	}
}