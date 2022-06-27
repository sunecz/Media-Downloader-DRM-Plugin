package sune.app.mediadownloader.drm.event;

public class CheckEventContext<C> extends EventContext<C> {
	
	private final String name;
	
	public CheckEventContext(C context, String name) {
		super(context);
		this.name = name;
	}
	
	public String name() {
		return name;
	}
}