package sune.app.mediadown.drm.tracker;

import sune.app.mediadown.event.tracker.SimpleTracker;
import sune.app.mediadown.gui.ProgressWindow.ProgressContext;

public class DecryptionProcessTracker extends SimpleTracker {
	
	private DecryptionProcessState state;
	
	public DecryptionProcessTracker() {
		state = DecryptionProcessState.NONE;
	}
	
	public void state(DecryptionProcessState state) {
		this.state = state;
		update();
	}
	
	@Override
	public double progress() {
		return ProgressContext.PROGRESS_INDETERMINATE;
	}
	
	@Override
	public String state() {
		return state.title();
	}
}