package sune.app.mediadownloader.drm;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.IEventType;
import sune.app.mediadown.event.Listener;
import sune.app.mediadown.event.tracker.DownloadTracker;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.update.CheckListener;
import sune.app.mediadown.update.FileCheckListener;
import sune.app.mediadown.update.FileChecker;
import sune.app.mediadown.update.FileDownloadListener;
import sune.app.mediadown.update.Requirements;
import sune.app.mediadown.update.Updater;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.OSUtils;
import sune.app.mediadown.util.PathSystem;
import sune.app.mediadown.util.Reflection3;
import sune.app.mediadown.util.UserAgent;
import sune.app.mediadown.util.Utils;
import sune.app.mediadown.util.Web;
import sune.app.mediadown.util.Web.HeadRequest;
import sune.app.mediadownloader.drm.event.CheckEventContext;
import sune.app.mediadownloader.drm.event.DRMBootstrapEvent;
import sune.app.mediadownloader.drm.event.DownloadEventContext;
import sune.app.mediadownloader.drm.event.LibrariesEventContext;
import sune.app.mediadownloader.drm.event.LibraryEventContext;
import sune.app.mediadownloader.drm.util.LibraryPaths;
import sune.util.load.Libraries;
import sune.util.load.Libraries.Library;
import sune.util.load.Libraries.LibraryLoadListener;
import sune.util.load.ModuleLoader;

public final class DRMBootstrap {
	
	private final List<Library> libraries = new ArrayList<>();
	private final String pathPrefixLib = "lib/drm/";
	private final String versionLib = "0001";
	private final String versionCef = "0001";
	private final String versionRes = "0001";
	
	private final boolean isDebug;
	private final Path logFile;
	private final boolean generateHashLists;
	private final boolean downloadWidevineCDM;
	
	private final DRMEventRegistry eventRegistry = new DRMEventRegistry();
	private final AtomicReference<Exception> exception = new AtomicReference<>();
	
	private Class<?> clazz;
	private final AtomicBoolean wasRun = new AtomicBoolean();
	
	private static final AtomicReference<DRMBootstrap> instance = new AtomicReference<>();
	
	private DRMBootstrap(boolean isDebug, Path logFile, boolean generateHashLists, boolean downloadWidevineCDM) {
		this.isDebug = isDebug;
		this.logFile = logFile;
		this.generateHashLists = generateHashLists;
		this.downloadWidevineCDM = downloadWidevineCDM;
	}
	
	public static final DRMBootstrap instance() {
		return instance.get();
	}
	
	private final void addLibrary(String relativePath) {
		addLibrary(relativePath, null);
	}
	
	private final void addLibrary(String relativePath, String name) {
		Path path = PathSystem.getPath(clazz, pathPrefixLib + relativePath);
		if(name == null) name = Utils.fileNameNoType(relativePath).replaceAll("-\\d+.*?$", "").replaceAll("[^A-Za-z0-9\\._]", ".");
		libraries.add(new Library(name, path));
	}
	
	private final void registerLibraries() {
		addLibrary("gluegen-rt.jar");
		addLibrary("gluegen-rt-natives-windows-amd64.jar");
		addLibrary("joal-2.3.2.jar");
		addLibrary("org.eclipse.swt.win32.win32.x86_64-3.118.0.jar");
		addLibrary("jogl-all.jar");
		addLibrary("jogl-all-natives-windows-amd64.jar");
		addLibrary("jcef.jar");
		addLibrary("proxy/activation-1.1.1.jar");
		addLibrary("proxy/alpn-api-1.1.2.v20150522.jar");
		addLibrary("proxy/org.osgi.core-4.3.1.jar");
		addLibrary("proxy/log4j-api-2.6.2.jar");
		addLibrary("proxy/javax.jms-api-2.0.1.jar");
		addLibrary("proxy/javax.mail-1.6.2.jar", "java.mail");
		addLibrary("proxy/log4j-1.2.17.jar");
		addLibrary("proxy/slf4j-api-1.7.24.jar");
		addLibrary("proxy/barchart-udt-bundle-2.3.0.jar");
		addLibrary("proxy/bcprov-jdk15on-1.51.jar");
		addLibrary("proxy/bcpkix-jdk15on-1.51.jar");
		addLibrary("proxy/commons-lang3-3.5.jar");
		addLibrary("proxy/javax.servlet-api-4.0.1.jar");
		addLibrary("proxy/commons-logging-1.2.jar");
		addLibrary("proxy/commons-cli-1.3.1.jar");
		addLibrary("proxy/commons-io-2.11.0.jar", "org.apache.commons.io");
		addLibrary("proxy/dnsjava-2.1.3.jar");
		addLibrary("proxy/dnssec4j-0.1.6.jar");
		addLibrary("proxy/error_prone_annotations-2.11.0.jar", "com.google.errorprone.annotations");
		addLibrary("proxy/jsr305-3.0.2.jar");
		addLibrary("proxy/guava-20.0.jar");
		addLibrary("proxy/javassist-3.19.0-GA.jar");
		addLibrary("proxy/jboss-modules-1.1.0.Beta1.jar");
		addLibrary("proxy/jboss-marshalling-1.4.11.Final.jar");
		addLibrary("proxy/jzlib-1.1.3.jar");
		addLibrary("proxy/npn-api-1.1.1.v20141010.jar");
		addLibrary("proxy/protobuf-java-2.5.0.jar");
		addLibrary("proxy/rxtx-2.1.7.jar");
		addLibrary("proxy/netty-tcnative-1.1.33.Fork26.jar");
		addLibrary("proxy/netty-all-4.0.44.Final.jar");
		addLibrary("proxy/littleproxy-1.1.2.jar");
		addLibrary("proxy/littleproxy-mitm-1.1.0.jar");
	}
	
	private final FileChecker localFileChecker(String relativePath) {
		Path currentDir = PathSystem.getPath(clazz, "");
		Path dir = PathSystem.getPath(clazz, relativePath);
		return new FileChecker.PrefixedFileChecker(dir, null, currentDir);
	}
	
	private final FileChecker libFileChecker(String version) {
		String dirRelativePath = "lib/drm/";
		FileChecker checker = localFileChecker(dirRelativePath);
		Path dir = PathSystem.getPath(clazz, dirRelativePath);
		libraries.forEach((library) -> {
			checker.addEntry(dir.resolve(library.getPath()).toAbsolutePath(), Requirements.ANY, version);
		});
		return checker;
	}
	
	private final FileChecker cefFileChecker(String version) throws IOException {
		String dirRelativePath = "lib/drm/cef";
		Path dir = PathSystem.getPath(clazz, dirRelativePath);
		FileChecker checker = localFileChecker(dirRelativePath);
		if(NIO.exists(dir)) {
			Files.walk(dir).forEach((file) -> {
				Path path = file.toAbsolutePath();
				if(Files.isRegularFile(path)) {
					checker.addEntry(path, Requirements.CURRENT, version);
				}
			});
		}
		return checker;
	}
	
	private final FileChecker resFileChecker(String version) throws IOException {
		String dirRelativePath = "resources/binary/drm";
		Path dir = PathSystem.getPath(clazz, dirRelativePath);
		FileChecker checker = localFileChecker(dirRelativePath);
		if(NIO.exists(dir)) {
			Files.walk(dir).forEach((file) -> {
				Path path = file.toAbsolutePath();
				if(Files.isRegularFile(path)) {
					checker.addEntry(path, Requirements.CURRENT, version);
				}
			});
		}
		return checker;
	}
	
	private final void generateHashList(FileChecker checker, Path output, boolean checkRequirements)
			throws IOException {
		if(!checker.generate((path) -> true, checkRequirements, true)) {
			throw new IllegalStateException("Unable to generate hash list.");
		}
		// Save the list of entries to a file
		NIO.save(output, checker.toString());
	}
	
	private final void checkLib(ResourceChecker checker, Path currentDir, boolean checkIntegrity) throws Exception {
		String baseURL = "https://app.sune.tech/mediadown/drm/lib/" + versionLib;
		FileChecker fileChecker = libFileChecker(versionLib);
		fileChecker.generate((file) -> true, false, checkIntegrity);
		checker.check(baseURL, currentDir, fileChecker, checkIntegrity, false);
	}
	
	private final void checkCef(ResourceChecker checker, Path currentDir, boolean checkIntegrity) throws Exception {
		String osInfo = OSUtils.getSystemName() + OSUtils.getSystemArch();
		String baseURL = "https://app.sune.tech/mediadown/drm/cef/" + osInfo + "/" + versionCef;
		FileChecker fileChecker = cefFileChecker(versionCef);
		fileChecker.generate((file) -> true, false, checkIntegrity);
		checker.check(baseURL, currentDir, fileChecker, checkIntegrity, true);
	}
	
	private final void checkRes(ResourceChecker checker, Path currentDir, boolean checkIntegrity) throws Exception {
		String osInfo = OSUtils.getSystemName() + OSUtils.getSystemArch();
		String baseURL = "https://app.sune.tech/mediadown/drm/res/" + osInfo + "/" + versionRes;
		FileChecker fileChecker = resFileChecker(versionRes);
		fileChecker.generate((file) -> true, false, checkIntegrity);
		checker.check(baseURL, currentDir, fileChecker, checkIntegrity, true);
	}
	
	public final void run(Class<?> clazz) throws Exception {
		// Return, if already run, otherwise set to true
		if(!wasRun.compareAndSet(false, true))
			return;
		
		instance.set(this);
		
		this.clazz = clazz;
		Path currentDir = PathSystem.getPath(clazz, "");
		boolean checkIntegrity = true;
		
		registerLibraries();
		
		if(generateHashLists) {
			generateHashList(libFileChecker(versionLib), currentDir.resolve("lib.sha1"), false);
			generateHashList(cefFileChecker(versionCef), currentDir.resolve("cef.sha1"), false);
			generateHashList(resFileChecker(versionRes), currentDir.resolve("res.sha1"), false);
			return; // Do not continue
		}
		
		ResourceChecker checker = new ResourceChecker();
		checkLib(checker, currentDir, checkIntegrity);
		checkCef(checker, currentDir, checkIntegrity);
		checkRes(checker, currentDir, checkIntegrity);
		
		Map<String, Library> map = libraries.stream()
				.filter((library) -> !ModuleLoader.isLoaded(library.getName()))
				.collect(Collectors.toMap(Library::getName, Function.identity(),
				                          (a, b) -> a, () -> new LinkedHashMap<>()));
		
		if(!map.isEmpty()) {
			LibraryLoadListener listener = new LibraryLoadListener() {
				
				@Override
				public void onLoading(Library library) {
					eventRegistry.call(DRMBootstrapEvent.LIBRARY_LOADING, new LibraryEventContext<>(DRMBootstrap.this, library, false));
				}
				
				@Override
				public void onLoaded(Library library, boolean success) {
					eventRegistry.call(DRMBootstrapEvent.LIBRARY_LOADED, new LibraryEventContext<>(DRMBootstrap.this, library, success));
				}
				
				@Override
				public void onNotLoaded(Library[] libraries) {
					eventRegistry.call(DRMBootstrapEvent.LIBRARIES_ERROR, new LibrariesEventContext<>(DRMBootstrap.this, libraries));
				}
			};
			
			// Load all registered libraries
			Reflection3.invokeStatic(Libraries.class, "load",
			                         new Class<?>[] { Map.class, LibraryLoadListener.class },
			                         map, listener);
			
			// Throw an exception that may have occurred, if any
			Exception ex = exception.get();
			if(ex != null) throw ex;
		}
		
		// Add the native libraries to paths so that they can be loaded
		LibraryPaths.prepend(PathSystem.getFullPath(clazz, "lib/drm/cef"));
	}
	
	public void error(Exception exception) {
		this.exception.set(exception);
	}
	
	public DRMEventRegistry eventRegistry() {
		return eventRegistry;
	}
	
	public final <T> void addEventListener(EventType<? extends IEventType, T> type, Listener<T> listener) {
		eventRegistry.add(type, listener);
	}
	
	public final <T> void removeEventListener(EventType<? extends IEventType, T> type, Listener<T> listener) {
		eventRegistry.remove(type, listener);
	}
	
	public boolean debug() {
		return isDebug;
	}
	
	public Path logFile() {
		return logFile;
	}
	
	public boolean generateHashLists() {
		return generateHashLists;
	}
	
	public boolean downloadWidevineCDM() {
		return downloadWidevineCDM;
	}
	
	public static final class Builder {
		
		private boolean isDebug;
		private Path logFile;
		private boolean generateHashLists;
		private boolean downloadWidevineCDM;
		
		public Builder() {
			this.isDebug = false;
			this.logFile = null;
			this.generateHashLists = false;
			this.downloadWidevineCDM = false;
		}
		
		public DRMBootstrap build() {
			return new DRMBootstrap(isDebug, logFile, generateHashLists, downloadWidevineCDM);
		}
		
		public void debug(boolean isDebug) {
			this.isDebug = isDebug;
		}
		
		public void logFile(Path logFile) {
			this.logFile = logFile;
		}
		
		public void generateHashLists(boolean generateHashLists) {
			this.generateHashLists = generateHashLists;
		}
		
		public void downloadWidevineCDM(boolean downloadWidevineCDM) {
			this.downloadWidevineCDM = downloadWidevineCDM;
		}
		
		public boolean debug() {
			return isDebug;
		}
		
		public Path logFile() {
			return logFile;
		}
		
		public boolean generateHashLists() {
			return generateHashLists;
		}
		
		public boolean downloadWidevineCDM() {
			return downloadWidevineCDM;
		}
	}
	
	private final class ResourceChecker {
		
		private final TrackerManager manager = new TrackerManager();
		
		private final ReadableByteChannel channel(DownloadEventContext<DRMBootstrap> context, URL url) throws Exception {
			long total = Web.size(new HeadRequest(url, UserAgent.CHROME));
			return new DownloadByteChannel(context, url.openStream(), total);
		}
		
		private final void download(URL url, Path dest) throws Exception {
			if(url == null || dest == null)
				throw new IllegalArgumentException();
			DownloadTracker tracker = new DownloadTracker(0L, false);
			tracker.setTrackerManager(manager);
			DownloadEventContext<DRMBootstrap> context
				= new DownloadEventContext<DRMBootstrap>(DRMBootstrap.this, url, dest, tracker);
			// To be sure, delete the file first, so a fresh copy is downloaded.
			NIO.deleteFile(dest);
			NIO.createDir(dest.getParent());
			try(ReadableByteChannel dbc = channel(context, url);
				FileChannel         fch = FileChannel.open(dest, CREATE, WRITE)) {
				// Notify the listener, if needed
				eventRegistry.call(DRMBootstrapEvent.RESOURCE_DOWNLOAD_BEGIN, context);
				// Actually download the file
				fch.transferFrom(dbc, 0L, Long.MAX_VALUE);
				// Notify the listener, if needed
				eventRegistry.call(DRMBootstrapEvent.RESOURCE_DOWNLOAD_END, context);
			}
		}
		
		private final Path ensurePathInDirectory(Path file, Path dir, boolean resolve) {
			if(file.isAbsolute()) {
				file = dir.relativize(file);
			}
			List<Path> paths = new ArrayList<>();
			int skip = 0;
			for(Path part : file) {
				if(skip > 0) { --skip; continue; }
				String name = part.getFileName().toString();
				if(name.equals("."))  { continue; }
				if(name.equals("..")) { ++skip; continue; }
				paths.add(part);
			}
			Path path = file;
			if(!paths.isEmpty()) {
				path = paths.stream().reduce((a, b) -> a.resolve(b)).get();
				if(resolve) path = dir.resolve(path);
			}
			return path;
		}
		
		private final void check(String baseURL, Path baseDir, FileChecker checker, boolean checkIntegrity,
				boolean allowNullLocalEntry) throws Exception {
			if(!NIO.exists(baseDir)) NIO.createDir(baseDir);
			Path localPath = PathSystem.getPath(clazz, "");
			Updater.checkResources(baseURL, baseDir, DRMConstants.TIMEOUT, checkListener(), checker,
				(url, file) -> download(Utils.url(url), ensurePathInDirectory(file, baseDir, true)),
				(file, webDir) -> Utils.urlConcat(webDir, ensurePathInDirectory(localPath.relativize(file), baseDir, false).toString().replace('\\', '/')),
				(file) -> ensurePathInDirectory(file, baseDir, true),
				(entryLoc, entryWeb) -> {
					if(entryLoc == null) return allowNullLocalEntry;
					Requirements requirements = entryWeb.getRequirements();
					return (requirements == Requirements.ANY
								|| requirements.equals(Requirements.CURRENT))
							&& (entryLoc.getVersion().equals(entryWeb.getVersion())) // Version check
							&& ((checkIntegrity // Muse be called before getHash()
									&& !entryLoc.getHash().equals(entryWeb.getHash()))
								|| !NIO.exists(entryLoc.getPath()));
				});
		}
		
		private final CheckListener checkListener() {
			return new CheckListener() {
				
				@Override
				public void compare(String name) {
					eventRegistry.call(DRMBootstrapEvent.RESOURCE_CHECK, new CheckEventContext<>(DRMBootstrap.this, name));
				}
				
				@Override public void begin() {}
				@Override public void end() {}
				@Override public FileCheckListener fileCheckListener() { return null; /* Not used */ }
				@Override public FileDownloadListener fileDownloadListener() { return null; /* Not used */ }
			};
		}
		
		private final class DownloadByteChannel implements ReadableByteChannel {
			
			// The original channel
			private final ReadableByteChannel channel;
			// The listener to which pass the information
			private final DownloadEventContext<DRMBootstrap> context;
			
			// Underlying input stream implementation
			private final class UIS extends InputStream {
				
				private final InputStream stream;
				
				public UIS(InputStream stream) {
					this.stream = stream;
				}
				
				@Override
				public int read() throws IOException {
					return stream.read();
				}
				
				@Override
				public int read(byte[] buf, int off, int len) throws IOException {
					// Call the underlying method
					int read = stream.read(buf, off, len);
					context.tracker().update(read);
					eventRegistry.call(DRMBootstrapEvent.RESOURCE_DOWNLOAD_UPDATE, context);
					return read;
				}
			}
			
			public DownloadByteChannel(DownloadEventContext<DRMBootstrap> context, InputStream stream, long total)
					throws IOException {
				this.context = context;
				this.channel = Channels.newChannel(new UIS(stream));
				context.tracker().updateTotal(total);
			}
			
			@Override
			public boolean isOpen() {
				return channel.isOpen();
			}
			
			@Override
			public void close() throws IOException {
				channel.close();
			}
			
			@Override
			public int read(ByteBuffer dst) throws IOException {
				return channel.read(dst);
			}
		}
	}
}