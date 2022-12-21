package sune.app.mediadownloader.drm.tracker;

public class RecordTracker extends TimeUpdatableTracker {
	
	public RecordTracker(double totalTime) {
		super(totalTime);
	}
	
	@Override
	public String state() {
		// TODO: Change
		return "RECORD";
	}
}