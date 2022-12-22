package sune.app.mediadownloader.drm.tracker;

import sune.app.mediadownloader.drm.integration.DRMProgressStates;

public class AnalyzeTracker extends TimeUpdatableTracker {
	
	private boolean isBuffering;
	
	public AnalyzeTracker(double totalTime) {
		super(totalTime);
	}
	
	public void setIsBuffering(boolean isBuffering) {
		this.isBuffering = isBuffering;
	}
	
	@Override
	public String state() {
		return DRMProgressStates.ANALYZE;
	}
	
	public boolean isBuffering() {
		return isBuffering;
	}
}