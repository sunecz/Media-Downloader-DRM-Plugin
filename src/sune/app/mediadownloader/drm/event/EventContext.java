package sune.app.mediadownloader.drm.event;

public class EventContext<C> {
	
	private final C context;
	
	public EventContext(C context) {
		this.context = context;
	}
	
	public C context() {
		return context;
	}
}