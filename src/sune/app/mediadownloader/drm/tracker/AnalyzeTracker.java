package sune.app.mediadownloader.drm.tracker;

public class AnalyzeTracker extends TimeUpdatableTracker {
	
	private boolean isBuffering;
	
	public AnalyzeTracker(double totalTime) {
		super(totalTime);
	}
	
	public void setIsBuffering(boolean isBuffering) {
		this.isBuffering = isBuffering;
	}
	
	public boolean isBuffering() {
		return isBuffering;
	}
}