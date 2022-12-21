package sune.app.mediadownloader.drm.tracker;

import java.util.concurrent.TimeUnit;

import sune.app.mediadown.event.tracker.SimpleTracker;
import sune.app.mediadown.event.tracker.TrackerView;
import sune.app.mediadown.util.Utils;

public class TimeUpdatableTracker extends SimpleTracker {
	
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
	
	@Override
	public String textProgress() {
		return null;
	}
	
	public double currentTime() {
		return currentTime;
	}
	
	public double totalTime() {
		return totalTime;
	}
	
	@Override
	public void view(TrackerView view) {
		view.current(Utils.OfFormat.time(currentTime, TimeUnit.SECONDS, false));
		view.total(Utils.OfFormat.time(totalTime, TimeUnit.SECONDS, false));
	}
}