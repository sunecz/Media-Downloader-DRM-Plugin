package sune.app.mediadownloader.drm.tracker;

import sune.app.mediadown.event.tracker.SimpleTracker;

public class TimeUpdatableTracker extends SimpleTracker {
	
	protected double currentTime;
	protected double totalTime;
	
	public TimeUpdatableTracker(double totalTime) {
		this.totalTime = totalTime;
	}
	
	public void update(double currentTime) {
		this.currentTime = currentTime;
		// notify the tracker manager
		manager.update();
	}
	
	@Override
	public double getProgress() {
		return currentTime / totalTime;
	}
	
	public double getCurrentTime() {
		return currentTime;
	}
	
	public double getTotalTime() {
		return totalTime;
	}
}