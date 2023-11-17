package sune.app.mediadown.drm.util;

import java.io.IOException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.Regex;

public final class AsciiUtils {
	
	private static final CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
	private static final Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
	private static final Regex regexNotAscii = Regex.of("[^\\p{ASCII}]");
	private static final Queue<Path> dirsToDelete = new ConcurrentLinkedQueue<>();
	
	static {
		Runtime.getRuntime().addShutdownHook(new Thread(AsciiUtils::maybeDeleteTempDirectories));
	}
	
	private AsciiUtils() {
	}
	
	private static final String toAscii(String string) {
		return regexNotAscii.replaceAll(Normalizer.normalize(string, Normalizer.Form.NFD), "");
	}
	
	private static final boolean isOnlyAscii(String string) {
		return asciiEncoder.canEncode(string);
	}
	
	private static final boolean isOnlyAscii(Path path) {
		for(int i = 0, l = path.getNameCount(); i < l; ++i) {
			if(!isOnlyAscii(path.getName(i).toString())) {
				return false;
			}
		}
		
		return true;
	}
	
	private static final Path asciiOnlyTempDir(Path absDir) {
		Path asciiDir = absDir.getRoot();
		
		for(int i = 0, l = absDir.getNameCount(); i < l; ++i) {
			String part = absDir.getName(i).toString();
			
			if(!isOnlyAscii(part)) {
				part = toAscii(part);
			}
			
			asciiDir = asciiDir.resolve(part);
		}
		
		return asciiDir;
	}
	
	private static final void createTempDirectories(Path dir) throws IOException {
		Path current = dir.getRoot();
		
		for(int i = 0, l = dir.getNameCount(); i < l; ++i) {
			current = current.resolve(dir.getName(i));
			
			if(!NIO.exists(current)) {
				NIO.createDir(current);
				
				// Make the directory only temporary
				synchronized(dirsToDelete) {
					dirsToDelete.add(current);
				}
			}
		}
	}
	
	public static final Path tempPath(Path desiredAbsDir, String asciiFileName) throws IOException {
		if(!isOnlyAscii(asciiFileName)) {
			throw new IllegalArgumentException("Non-ASCII file name");
		}
		
		Path tempPath;
		
		// Try the desired path first. For Czech users this will probably fail,
		// since they often have Czech characters with diacritics in the path.
		tempPath = desiredAbsDir.resolve(asciiFileName);
		if(isOnlyAscii(tempPath)) return tempPath;
		
		// Try the temporary directory, this should be ASCII-only for most users.
		tempPath = tempDir.resolve(asciiFileName);
		if(isOnlyAscii(tempPath)) return tempPath;
		
		// If all above fails, try to recreate the desiredDir but with ASCII-only
		// characters in the names. However, this may fail since permissions may
		// vary from user to user to create the required directories.
		Path asciiTempDir = asciiOnlyTempDir(desiredAbsDir);
		createTempDirectories(asciiTempDir);
		tempPath = asciiTempDir.resolve(asciiFileName);
		
		return tempPath;
	}
	
	public static final void maybeDeleteTempDirectories() {
		List<Path> dirs = new ArrayList<>();
		
		// Make a snapshot of the directories to delete
		synchronized(dirsToDelete) {
			dirs.addAll(dirsToDelete);
		}
		
		for(Path dir : dirs) {
			try {
				NIO.delete(dir);
			} catch(IOException ex) {
				// Ignore, nothing to do with it
			}
		}
	}
}