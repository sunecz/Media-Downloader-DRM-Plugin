package sune.app.mediadown.drm.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;
import java.util.logging.Level;

import sune.app.mediadown.InternalState;
import sune.app.mediadown.logging.Log;
import sune.app.mediadown.util.NIO;

public final class Common {
	
	private static final ProcessListener NOOP_LISTENER = new NoopProcessListener();
	
	private static volatile boolean initialized;
	private static Log log;
	private static Level logLevel;
	
	private Common() {
	}
	
	private static final void checkInitialized() {
		if(!initialized) {
			throw new IllegalStateException("Not initialized");
		}
	}
	
	public static final void initialize(Level logLevel) {
		if(initialized) {
			return; // Ignore subsequent initializations
		}
		
		log = Log.initialize("plugin:drm", "drm.log", Common.logLevel = logLevel);
		initialized = true;
	}
	
	public static final Log log() {
		return log;
	}
	
	public static final void logDebug(String message, Object... args) {
		Log log;
		if((log = Common.log) == null) {
			return;
		}
		
		log.debug(message, args);
	}
	
	public static final ProcessListener newProcessListener(String name) throws IOException {
		checkInitialized();
		return logLevel.intValue() <= Level.FINEST.intValue()
					? new LoggingProcessListener(name)
					: NOOP_LISTENER;
	}
	
	public static interface ProcessListener extends Consumer<String>, AutoCloseable {
		
		void close() throws IOException;
	}
	
	private static final class NoopProcessListener implements ProcessListener {
		
		@Override public void accept(String line) {}
		@Override public void close() {}
	}
	
	private static final class LoggingProcessListener implements ProcessListener {
		
		private static final int STATE_OPEN    = 0b1 << 0;
		private static final int STATE_CLOSING = 0b1 << 1;
		private static final int STATE_CLOSED  = 0b1 << 2;
		
		private final Path path;
		private final BufferedWriter writer;
		private final InternalState state;
		
		private LoggingProcessListener(String name) throws IOException {
			writer = Files.newBufferedWriter(
				path = NIO.uniqueFile("md_plugin_drm-" + name + "-", ".log"),
				StandardOpenOption.CREATE, StandardOpenOption.WRITE
			);
			state = new InternalState(STATE_OPEN);
		}
		
		@Override
		public void accept(String line) {
			try {
				writer.write(line);
				writer.write('\n');
			} catch(IOException ex) {
				// Ignore
			}
		}
		
		@Override
		public void close() throws IOException {
			if(!state.compareAndSetBit(false, STATE_CLOSING)) {
				return; // Closing or closed
			}
			
			writer.close();
			log.debug(NIO.read(path));
			state.setAndUnset(STATE_CLOSED, STATE_OPEN);
		}
	}
}