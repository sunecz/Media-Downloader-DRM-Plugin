package sune.app.mediadownloader.drm.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FilesManager {
	
	private final Set<Path> pathsToDelete = new LinkedHashSet<>();
	
	public void delete(Path path) {
		pathsToDelete.add(path);
	}
	
	public List<Path> pathsToDelete() {
		return new ArrayList<>(pathsToDelete);
	}
}