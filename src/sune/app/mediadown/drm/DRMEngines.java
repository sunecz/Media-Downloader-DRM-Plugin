package sune.app.mediadown.drm;

import java.net.URI;
import java.util.Collection;

import sune.app.mediadown.util.ObjectHolder;

public final class DRMEngines {
	
	private static final ObjectHolder<String, DRMEngine> holder = new ObjectHolder<>();
	
	public static final void add(String name, Class<? extends DRMEngine> clazz) { holder.add(name, clazz); }
	public static final DRMEngine get(String name) { return holder.get(name); }
	public static final Collection<DRMEngine> all() { return holder.all(); }
	
	public static final DRMEngine fromURI(URI uri) {
		return holder.stream()
					.filter((o) -> o.isCompatibleURI(uri))
					.findFirst().orElse(null);
	}
	
	// Forbid anyone to create an instance of this class
	private DRMEngines() {
	}
}