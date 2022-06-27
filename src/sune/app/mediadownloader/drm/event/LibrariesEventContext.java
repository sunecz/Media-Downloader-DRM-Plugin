package sune.app.mediadownloader.drm.event;

import sune.util.load.Libraries.Library;

public class LibrariesEventContext<C> extends EventContext<C> {
	
	private final Library[] libraries;
	
	public LibrariesEventContext(C context, Library[] libraries) {
		super(context);
		this.libraries = libraries;
	}
	
	public Library[] libraries() {
		return libraries;
	}
}