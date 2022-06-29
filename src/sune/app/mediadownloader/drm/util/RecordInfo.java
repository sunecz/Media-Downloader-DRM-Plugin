package sune.app.mediadownloader.drm.util;

import java.nio.file.Path;
import java.util.List;

public final class RecordInfo {
	
	private final Path path;
	private final List<Cut.OfDouble> videoCuts;
	private final List<Cut.OfDouble> audioCuts;
	private final double frameRate;
	private final int sampleRate;
	private final double audioOffset;
	private final Cut.OfDouble cutOff;
	
	public RecordInfo(Path path, List<Cut.OfDouble> videoCuts, List<Cut.OfDouble> audioCuts, double frameRate,
			int sampleRate, double audioOffset, Cut.OfDouble cutOff) {
		this.path = path;
		this.videoCuts = videoCuts;
		this.audioCuts = audioCuts;
		this.frameRate = frameRate;
		this.sampleRate = sampleRate;
		this.audioOffset = audioOffset;
		this.cutOff = cutOff;
	}
	
	public Path path() {
		return path;
	}
	
	public List<Cut.OfDouble> videoCuts() {
		return videoCuts;
	}
	
	public List<Cut.OfDouble> audioCuts() {
		return audioCuts;
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
	
	public Cut.OfDouble cutOff() {
		return cutOff;
	}
}