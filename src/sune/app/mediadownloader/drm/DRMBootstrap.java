package sune.app.mediadownloader.drm;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.MediaDownloader.Versions.VersionEntryAccessor;
import sune.app.mediadown.Shared;
import sune.app.mediadown.download.DownloadConfiguration;
import sune.app.mediadown.download.FileDownloader;
import sune.app.mediadown.download.InputStreamChannelFactory;
import sune.app.mediadown.event.CheckEvent;
import sune.app.mediadown.event.DownloadEvent;
import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventBindable;
import sune.app.mediadown.event.EventRegistry;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.Listener;
import sune.app.mediadown.event.tracker.DownloadTracker;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.library.Libraries;
import sune.app.mediadown.library.Library;
import sune.app.mediadown.library.LibraryEvent;
import sune.app.mediadown.update.FileChecker;
import sune.app.mediadown.update.Requirements;
import sune.app.mediadown.update.Updater;
import sune.app.mediadown.update.Version;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.OSUtils;
import sune.app.mediadown.util.PathSystem;
import sune.app.mediadown.util.Utils;
import sune.app.mediadown.util.Web.GetRequest;
import sune.app.mediadownloader.drm.event.CheckEventContext;
import sune.app.mediadownloader.drm.event.DRMBootstrapEvent;
import sune.app.mediadownloader.drm.event.DownloadEventContext;
import sune.app.mediadownloader.drm.event.LibrariesEventContext;
import sune.app.mediadownloader.drm.event.LibraryEventContext;
import sune.app.mediadownloader.drm.util.LibraryPaths;
import sune.util.load.ModuleLoader;

public final class DRMBootstrap implements EventBindable<EventType> {
	
	private final Libraries libraries = Libraries.create();
	private final String pathPrefixLib = "lib/drm/";
	private final String versionLib = "0002";
	private final String versionCef = "0003";
	private final String versionRes = "0002";
	
	private final boolean isDebug;
	private final Path logFile;
	private final boolean generateHashLists;
	private final boolean downloadWidevineCDM;
	
	private final EventRegistry<EventType> eventRegistry = new EventRegistry<>();
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
		libraries.add(path, name);
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
		return new FileChecker.PrefixedFileChecker(dir, currentDir);
	}
	
	private final FileChecker libFileChecker(String version) {
		String dirRelativePath = "lib/drm/";
		FileChecker checker = localFileChecker(dirRelativePath);
		Path dir = PathSystem.getPath(clazz, dirRelativePath);
		libraries.all().forEach((library) -> {
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
			throws Exception {
		checker.generate((path) -> true, checkRequirements, (path) -> true);
		// Save the list of entries to a file
		NIO.save(output, checker.toString());
	}
	
	private final void checkLib(ResourceChecker checker, Path currentDir, boolean checkIntegrity) throws Exception {
		String baseURL = "https://app.sune.tech/mediadown/drm/lib/" + versionLib;
		FileChecker fileChecker = libFileChecker(versionLib);
		VersionEntryAccessor version = VersionEntryAccessor.of("drm_lib");
		Version verLocal = version.get();
		Version verRemote = Version.of(versionLib);
		
		if(verLocal.compareTo(verRemote) != 0 || verLocal == Version.UNKNOWN) {
			checkIntegrity = true;
		}
		
		boolean checkHashes = checkIntegrity;
		fileChecker.generate((file) -> true, false, (path) -> checkHashes);
		checker.check(baseURL, currentDir, fileChecker, checkHashes, false, false);
		version.set(verRemote);
	}
	
	private final void checkCef(ResourceChecker checker, Path currentDir, boolean checkIntegrity) throws Exception {
		String osInfo = OSUtils.getSystemName() + OSUtils.getSystemArch();
		String baseURL = "https://app.sune.tech/mediadown/drm/cef/" + osInfo + "/" + versionCef;
		FileChecker fileChecker = cefFileChecker(versionCef);
		VersionEntryAccessor version = VersionEntryAccessor.of("drm_cef");
		Version verLocal = version.get();
		Version verRemote = Version.of(versionCef);
		
		if(verLocal.compareTo(verRemote) != 0 || verLocal == Version.UNKNOWN) {
			checkIntegrity = true;
		}
		
		boolean checkHashes = checkIntegrity;
		fileChecker.generate((file) -> true, false, (path) -> checkHashes);
		checker.check(baseURL, currentDir, fileChecker, checkHashes, true, true);
		version.set(verRemote);
	}
	
	private final void checkRes(ResourceChecker checker, Path currentDir, boolean checkIntegrity) throws Exception {
		String osInfo = OSUtils.getSystemName() + OSUtils.getSystemArch();
		String baseURL = "https://app.sune.tech/mediadown/drm/res/" + osInfo + "/" + versionRes;
		FileChecker fileChecker = resFileChecker(versionRes);
		VersionEntryAccessor version = VersionEntryAccessor.of("drm_res");
		Version verLocal = version.get();
		Version verRemote = Version.of(versionRes);
		
		if(verLocal.compareTo(verRemote) != 0 || verLocal == Version.UNKNOWN) {
			checkIntegrity = true;
		}
		
		boolean checkHashes = checkIntegrity;
		fileChecker.generate((file) -> true, false, (path) -> checkHashes);
		checker.check(baseURL, currentDir, fileChecker, checkHashes, true, false);
		version.set(verRemote);
	}
	
	public final void run(Class<?> clazz) throws Exception {
		// Return, if already run, otherwise set to true
		if(!wasRun.compareAndSet(false, true))
			return;
		
		instance.set(this);
		
		this.clazz = clazz;
		Path currentDir = PathSystem.getPath(clazz, "");
		
		registerLibraries();
		
		if(generateHashLists) {
			generateHashList(libFileChecker(versionLib), currentDir.resolve("lib.sha1"), false);
			generateHashList(cefFileChecker(versionCef), currentDir.resolve("cef.sha1"), false);
			generateHashList(resFileChecker(versionRes), currentDir.resolve("res.sha1"), false);
		}
		
		if(MediaDownloader.AppArguments.isUpdateEnabled()) {
			ResourceChecker checker = new ResourceChecker();
			boolean checkIntegrity = MediaDownloader.configuration().isCheckResourcesIntegrity();
			
			checkLib(checker, currentDir, checkIntegrity);
			checkCef(checker, currentDir, checkIntegrity);
			checkRes(checker, currentDir, checkIntegrity);
		}
		
		Map<String, Library> map = libraries.all().stream()
				.filter((library) -> !ModuleLoader.isLoaded(library.getName()))
				.collect(Collectors.toMap(Library::getName, Function.identity(),
				                          (a, b) -> a, () -> new LinkedHashMap<>()));
		
		if(!map.isEmpty()) {
			List<Library> notLoaded = new LinkedList<>();
			
			libraries.on(LibraryEvent.LOADING, (library) -> {
				eventRegistry.call(DRMBootstrapEvent.LIBRARY_LOADING, new LibraryEventContext<>(DRMBootstrap.this, library, false));
			});
			
			libraries.on(LibraryEvent.LOADED, (library) -> {
				eventRegistry.call(DRMBootstrapEvent.LIBRARY_LOADED, new LibraryEventContext<>(DRMBootstrap.this, library, true));
			});
			
			libraries.on(LibraryEvent.NOT_LOADED, (pair) -> {
				notLoaded.add(pair.a);
			});
			
			// Load all registered libraries
			boolean success = libraries.load();
			
			if(!success) {
				eventRegistry.call(DRMBootstrapEvent.LIBRARIES_ERROR, new LibrariesEventContext<>(DRMBootstrap.this, notLoaded));
			}
			
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
	
	public EventRegistry<EventType> eventRegistry() {
		return eventRegistry;
	}
	
	@Override
	public <V> void addEventListener(Event<? extends EventType, V> event, Listener<V> listener) {
		eventRegistry.add(event, listener);
	}
	
	@Override
	public <V> void removeEventListener(Event<? extends EventType, V> event, Listener<V> listener) {
		eventRegistry.remove(event, listener);
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
		
		private final Path download(URI uri, Path destination, boolean useCompressedStreams) throws Exception {
			Objects.requireNonNull(uri);
			Objects.requireNonNull(destination);
			
			// To be sure, delete the file first, so a fresh copy is downloaded.
			NIO.deleteFile(destination);
			NIO.createDir(destination.getParent());
			
			FileDownloader downloader = new FileDownloader(manager);
			
			if(useCompressedStreams) {
				downloader.setResponseChannelFactory(InputStreamChannelFactory.GZIP.ofDefault());
			}
			
			DownloadTracker tracker = new DownloadTracker();
			downloader.setTracker(tracker);
			
			DownloadEventContext<DRMBootstrap> context
				= new DownloadEventContext<>(DRMBootstrap.this, uri, destination, tracker);
			
			downloader.addEventListener(DownloadEvent.BEGIN, (d) -> {
				eventRegistry.call(DRMBootstrapEvent.RESOURCE_DOWNLOAD_BEGIN, context);
			});
			
			downloader.addEventListener(DownloadEvent.UPDATE, (pair) -> {
				eventRegistry.call(DRMBootstrapEvent.RESOURCE_DOWNLOAD_UPDATE, context);
			});
			
			downloader.addEventListener(DownloadEvent.END, (d) -> {
				eventRegistry.call(DRMBootstrapEvent.RESOURCE_DOWNLOAD_END, context);
			});
			
			GetRequest request = new GetRequest(Utils.url(uri), Shared.USER_AGENT);
			downloader.start(request, destination, DownloadConfiguration.ofDefault());
			
			return destination;
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
				boolean allowNullLocalEntry, boolean useCompressedStreams) throws Exception {
			if(!NIO.exists(baseDir)) {
				NIO.createDir(baseDir);
			}
			
			Path localPath = PathSystem.getPath(clazz, "");
			Updater updater = Updater.ofResources(baseURL, baseDir, DRMConstants.TIMEOUT, checker,
				(url, file) -> download(Utils.uri(url), ensurePathInDirectory(file, baseDir, true), useCompressedStreams),
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
				}, null);
			
			updater.addEventListener(CheckEvent.COMPARE, (name) -> {
				eventRegistry.call(DRMBootstrapEvent.RESOURCE_CHECK, new CheckEventContext<>(DRMBootstrap.this, name));
			});
			
			updater.check();
		}
	}
}