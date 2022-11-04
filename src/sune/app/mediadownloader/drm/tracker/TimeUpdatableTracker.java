package sune.app.mediadownloader.drm.tracker;

import sune.app.mediadown.event.tracker.SimpleTracker;

public class TimeUpdatableTracker extends SimpleTracker {
	
	// TODO: Refactor
	
	protected double currentTime;
	protected double totalTime;
	
	public TimeUpdatableTracker(double totalTime) {
		this.totalTime = totalTime;
	}
	
	public void update(double currentTime) {
		this.currentTime = currentTime;
		update();
	}
	
	@Override
	public double progress() {
		return currentTime / totalTime;
	}
	
	public double getCurrentTime() {
		return currentTime;
	}
	
	public double getTotalTime() {
		return totalTime;
	}
}