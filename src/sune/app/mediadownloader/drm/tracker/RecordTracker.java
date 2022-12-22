package sune.app.mediadownloader.drm.tracker;

import sune.app.mediadownloader.drm.integration.DRMProgressStates;

public class RecordTracker extends TimeUpdatableTracker {
	
	public RecordTracker(double totalTime) {
		super(totalTime);
	}
	
	@Override
	public String state() {
		return DRMProgressStates.RECORD;
	}
}