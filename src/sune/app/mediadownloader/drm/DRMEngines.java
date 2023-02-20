package sune.app.mediadownloader.drm;

import java.util.Collection;

import sune.app.mediadown.net.Net;
import sune.app.mediadown.util.ObjectHolder;

public final class DRMEngines {
	
	private static final ObjectHolder<String, DRMEngine> holder = new ObjectHolder<>();
	
	public static final void add(String name, Class<? extends DRMEngine> clazz) { holder.add(name, clazz); }
	public static final DRMEngine get(String name) { return holder.get(name); }
	public static final Collection<DRMEngine> all() { return holder.all(); }
	
	public static final DRMEngine fromURL(String url) {
		return Net.isValidURI(url)
					? holder.stream()
						.filter((o) -> o.isCompatibleURL(url))
						.findFirst().orElse(null)
					: null;
	}
	
	// Forbid anyone to create an instance of this class
	private DRMEngines() {
	}
}