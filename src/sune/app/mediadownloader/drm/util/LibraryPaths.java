package sune.app.mediadownloader.drm.util;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sune.app.mediadown.util.Reflection2;
import sune.app.mediadown.util.Reflection3;

public final class LibraryPaths {
	
	private static final ClassLoader CLASS_LOADER = ClassLoader.getSystemClassLoader();
	private static final String PROPERTY_NAME = "java.library.path";
	
	private static String beforeValue;
	private static Set<String> libraryPaths;

	private static final String[] libraryPaths()
			throws NoSuchMethodException,
			       SecurityException,
			       IllegalAccessException,
			       InvocationTargetException,
			       NoSuchFieldException,
			       IllegalArgumentException {
		return (String[]) Reflection3.invoke(CLASS_LOADER, ClassLoader.class, "initializePath", PROPERTY_NAME);
	}
	
	private static final void ensureLibraryPaths() {
		if(libraryPaths == null) {
			try {
				libraryPaths = new HashSet<>(List.of(libraryPaths()));
			} catch(NoSuchMethodException
						| SecurityException
						| IllegalAccessException
						| InvocationTargetException
						| NoSuchFieldException
						| IllegalArgumentException ex) {
				throw new IllegalStateException("Unable to get library paths.", ex);
			}
		}
	}
	
	private static final <T> T[] prepend(T[] array, T value) {
		@SuppressWarnings("unchecked")
		T[] newArray = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length + 1);
		newArray[0] = value;
		System.arraycopy(array, 0, newArray, 1, array.length);
		return newArray;
	}
	
	private static final void prependToPaths(String path) {
		String[] paths = Reflection2.getField(ClassLoader.class, CLASS_LOADER, "usr_paths");
		paths = prepend(paths, path);
		Reflection2.setField(ClassLoader.class, CLASS_LOADER, "usr_paths", paths);
	}
	
	private static final String fixPath(String path) {
		return path.replace('/', File.separatorChar);
	}
	
	public static final void prepend(String path) {
		ensureLibraryPaths();
		path = fixPath(path);
		if(libraryPaths.contains(path))
			return; // Already in paths
		libraryPaths.add(path);
		String currentValue = System.getProperty(PROPERTY_NAME);
		if(beforeValue == null) beforeValue = currentValue;
		String newValue = path + File.pathSeparatorChar + currentValue;
		System.setProperty(PROPERTY_NAME, newValue);
		prependToPaths(path);
	}
	
	// Forbid anyone to create an instance of this class
	private LibraryPaths() {
	}
}