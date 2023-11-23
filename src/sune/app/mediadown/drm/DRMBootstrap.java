package sune.app.mediadown.drm;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import sune.app.mediadown.MediaDownloader;
import sune.app.mediadown.MediaDownloader.Versions.VersionEntryAccessor;
import sune.app.mediadown.download.DownloadConfiguration;
import sune.app.mediadown.download.FileDownloader;
import sune.app.mediadown.download.InputStreamFactory;
import sune.app.mediadown.drm.event.CheckEventContext;
import sune.app.mediadown.drm.event.DRMBootstrapEvent;
import sune.app.mediadown.drm.event.DownloadEventContext;
import sune.app.mediadown.event.CheckEvent;
import sune.app.mediadown.event.DownloadEvent;
import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventBindable;
import sune.app.mediadown.event.EventRegistry;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.Listener;
import sune.app.mediadown.event.tracker.DownloadTracker;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.net.Net;
import sune.app.mediadown.net.Web.Request;
import sune.app.mediadown.plugin.Plugin;
import sune.app.mediadown.plugin.PluginLoaderContext;
import sune.app.mediadown.update.FileChecker;
import sune.app.mediadown.update.Requirements;
import sune.app.mediadown.update.Updater;
import sune.app.mediadown.update.Version;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.OSUtils;
import sune.app.mediadown.util.PathSystem;

public final class DRMBootstrap implements EventBindable<EventType> {
	
	private final String versionRes = "0005";
	
	private final boolean generateHashLists;
	
	private final EventRegistry<EventType> eventRegistry = new EventRegistry<>();
	private final AtomicReference<Exception> exception = new AtomicReference<>();
	
	private Class<?> clazz;
	private final AtomicBoolean wasRun = new AtomicBoolean();
	
	private static final AtomicReference<DRMBootstrap> instance = new AtomicReference<>();
	
	private DRMBootstrap(boolean generateHashLists) {
		this.generateHashLists = generateHashLists;
	}
	
	public static final DRMBootstrap instance() {
		return instance.get();
	}
	
	private final FileChecker localFileChecker(String relativePath) {
		Path currentDir = PathSystem.getPath(clazz, "");
		Path dir = PathSystem.getPath(clazz, relativePath);
		return new FileChecker.PrefixedFileChecker(dir, currentDir);
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
		checker.check(baseURL, currentDir, fileChecker, checkHashes, true, true);
		version.set(verRemote);
	}
	
	private final void doCleanup() throws Exception {
		Plugin plugin = PluginLoaderContext.getContext().getPlugin().instance();
		Version previous = MediaDownloader.Versions.get("drm_plugin");
		Version current = Version.of(plugin.version());
		DRMUpdateCleanup.cleanup(current, previous);
	}
	
	public final void run(Class<?> clazz) throws Exception {
		// Return, if already run, otherwise set to true
		if(!wasRun.compareAndSet(false, true)) {
			return;
		}
		
		instance.set(this);
		
		this.clazz = clazz;
		Path currentDir = PathSystem.getPath(clazz, "");
		
		if(generateHashLists) {
			generateHashList(resFileChecker(versionRes), currentDir.resolve("res.sha1"), false);
		}
		
		doCleanup();
		
		if(MediaDownloader.AppArguments.isUpdateEnabled()) {
			ResourceChecker checker = new ResourceChecker();
			boolean checkIntegrity = MediaDownloader.configuration().isCheckResourcesIntegrity();
			checkRes(checker, currentDir, checkIntegrity);
		}
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
	
	public boolean generateHashLists() {
		return generateHashLists;
	}
	
	public static final class Builder {
		
		private boolean generateHashLists;
		
		public Builder() {
			this.generateHashLists = false;
		}
		
		public DRMBootstrap build() {
			return new DRMBootstrap(generateHashLists);
		}
		
		public void generateHashLists(boolean generateHashLists) {
			this.generateHashLists = generateHashLists;
		}
		
		public boolean generateHashLists() {
			return generateHashLists;
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
				downloader.setResponseStreamFactory(InputStreamFactory.GZIP.ofDefault());
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
			
			Request request = Request.of(uri).GET();
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
				(url, file) -> download(Net.uri(url), ensurePathInDirectory(file, baseDir, true), useCompressedStreams),
				(file, webDir) -> Net.uriConcat(webDir, ensurePathInDirectory(localPath.relativize(file), baseDir, false).toString().replace('\\', '/')),
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