package sune.app.mediadownloader.drm.event;

import java.util.List;

import sune.app.mediadown.library.Library;

public class LibrariesEventContext<C> extends EventContext<C> {
	
	private final List<Library> libraries;
	
	public LibrariesEventContext(C context, List<Library> libraries) {
		super(context);
		this.libraries = libraries;
	}
	
	public List<Library> libraries() {
		return libraries;
	}
}