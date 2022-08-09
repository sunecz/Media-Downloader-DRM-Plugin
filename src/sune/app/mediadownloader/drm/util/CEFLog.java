package sune.app.mediadownloader.drm.util;

import static com.sun.nio.file.SensitivityWatchEventModifier.HIGH;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import sune.app.mediadown.util.Threads;

public final class CEFLog {
	
	private static CEFLog INSTANCE;
	
	private final List<Consumer<String>> readers = new ArrayList<>();
	private final LogReader reader;
	
	private CEFLog(Path path) {
		this.reader = new LogReader(path);
	}
	
	public static final void initialize(Path path) {
		if(INSTANCE != null) return; // Already initialized
		INSTANCE = new CEFLog(path);
	}
	
	public static final CEFLog instance() {
		return INSTANCE;
	}
	
	public final void registerReader(Consumer<String> reader) {
		readers.add(Objects.requireNonNull(reader));
	}
	
	public final void start() {
		reader.start();
	}
	
	public final void stop() throws IOException {
		reader.stop();
	}
	
	public final void await() throws IOException {
		reader.await();
	}
	
	public final void close() throws IOException {
		reader.close();
	}
	
	public final Path path() {
		return reader.path();
	}
	
	private final class LogReader {
		
		private final class PathWatchEvent implements WatchEvent<Path> {
			
			@Override public Kind<Path> kind() { return ENTRY_MODIFY; }
			@Override public int count() { return 1; }
			@Override public Path context() { return path; }
		}
		
		private final Path path;
		private final AtomicReference<BufferedReader> reader = new AtomicReference<>();
		private final AtomicReference<IOException> exception = new AtomicReference<>();
		private final AtomicBoolean started = new AtomicBoolean();
		private final AtomicBoolean running = new AtomicBoolean();
		private Thread thread;
		private WatchService watcher;
		private final StateMutex mutex = new StateMutex();
		
		public LogReader(Path path) {
			this.path = path;
		}
		
		private final void readNext(BufferedReader reader) throws IOException {
			for(String line; running.get() && (line = reader.readLine()) != null;) {
				for(Consumer<String> r : readers) {
					r.accept(line);
				}
			}
		}
		
		private final void run() {
			try {
				started.set(true);
				BufferedReader r = Files.newBufferedReader(path);
				reader.set(r);
				running.set(true);
				Path dir = path.getParent();
				watcher = FileSystems.getDefault().newWatchService();
				dir.register(watcher, new Kind[] { ENTRY_MODIFY, ENTRY_CREATE }, HIGH);
				for(WatchKey watchKey; running.get();) {
					try {
						watchKey = watcher.poll(500, TimeUnit.MILLISECONDS);
					} catch(InterruptedException ex) {
						break;
					}
					List<WatchEvent<?>> events = watchKey != null
							? watchKey.pollEvents()
							: List.of(new PathWatchEvent());
					for(WatchEvent<?> event : events) {
						Kind<?> kind = event.kind();
						if(kind == OVERFLOW) continue;
						@SuppressWarnings("unchecked")
						Path file = dir.resolve(((WatchEvent<Path>) event).context());
						if(file.equals(path)) readNext(r);
					}
					if(watchKey != null && !watchKey.reset()) {
						break;
					}
				}
			} catch(IOException ex) {
				exception.set(ex);
			} finally {
				running.set(false);
				reader.set(null);
				try {
					watcher.close();
				} catch(IOException ex) {
					// Do not replace previous exception, if any
					if(exception.get() == null)
						exception.set(ex);
				}
				mutex.unlock();
			}
		}
		
		public void start() {
			thread = Threads.newThreadUnmanaged(this::run);
			thread.start();
		}
		
		public void stop() throws IOException {
			close();
		}
		
		public void await() throws IOException {
			mutex.await();
			IOException ex = exception.get();
			if(ex != null) throw ex;
		}
		
		public void close() throws IOException {
			running.set(false);
			if(started.get()) {
				BufferedReader br = reader.get();
				if(br != null) br.close();
			}
		}
		
		public Path path() {
			return path;
		}
	}
}