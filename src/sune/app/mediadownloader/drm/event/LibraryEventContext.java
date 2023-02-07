package sune.app.mediadownloader.drm.event;

import sune.app.mediadown.library.Library;

public class LibraryEventContext<C> extends EventContext<C> {
	
	private final Library library;
	private final boolean success;
	
	public LibraryEventContext(C context, Library library, boolean success) {
		super(context);
		this.library = library;
		this.success = success;
	}
	
	public Library library() {
		return library;
	}
	
	public boolean success() {
		return success;
	}
}