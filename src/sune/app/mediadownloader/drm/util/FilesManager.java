package sune.app.mediadownloader.drm.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import sune.app.mediadown.util.NIO;

public final class FilesManager {
	
	private final Set<Path> pathsToDelete = new LinkedHashSet<>();
	
	public void delete(Path path) {
		pathsToDelete.add(Objects.requireNonNull(path));
	}
	
	public void deleteNow(Path path) throws IOException {
		NIO.delete(Objects.requireNonNull(path));
	}
	
	public void deleteAll() throws IOException {
		for(Path path : pathsToDelete) {
			NIO.delete(path);
		}
	}
	
	public List<Path> pathsToDelete() {
		return List.copyOf(pathsToDelete);
	}
}