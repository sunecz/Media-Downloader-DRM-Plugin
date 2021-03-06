package sune.app.mediadownloader.drm;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.cef.CefApp;
import org.slf4j.Logger;

import sune.app.mediadown.event.tracker.DownloadTracker;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.OSUtils;
import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.PathSystem;
import sune.app.mediadown.util.UserAgent;
import sune.app.mediadown.util.Utils;
import sune.app.mediadown.util.Web;
import sune.app.mediadown.util.Web.GetRequest;
import sune.app.mediadown.util.Web.PostRequest;
import sune.app.mediadown.util.Web.Request;
import sune.app.mediadown.util.Web.StreamResponse;
import sune.app.mediadown.util.Web.StringResponse;
import sune.app.mediadownloader.drm.event.WidevineCDMEvent;
import sune.app.mediadownloader.drm.integration.IntegrationUtils;
import sune.app.mediadownloader.drm.util.CEFLog;
import sune.app.mediadownloader.drm.util.CRXExtractor;
import sune.app.mediadownloader.drm.util.StateMutex;
import sune.util.ssdf2.SSDCollection;
import sune.util.ssdf2.SSDF;

public final class WidevineCDM {
	
	private static final Logger logger = DRMLog.get();

	private static final Pattern PATTERN_VERSION = Pattern.compile("^\\d+(?:\\.\\d+){3}$");
	private static DRMContext context;
	
	// Forbid anyone to create an instance of this class
	private WidevineCDM() {
	}
	
	private static final void checkOS() {
		if(!OSUtils.isWindows())
			throw new IllegalStateException("Only supported on Windows for now.");
	}
	
	public static final void download(DRMContext drmContext, String requestURL) throws Exception {
		if(logger.isDebugEnabled())
			logger.debug("Sending Widevine CDM request...");
		context = drmContext;
		SSDCollection json = WidevineCDMDownloadRequest.send(requestURL);
		SSDCollection app = json.getCollection("response.app.0");
		String baseURL = app.getString("updatecheck.urls.url.0.codebase");
		String packageName = app.getString("updatecheck.manifest.packages.package.0.name");
		String packageVersion = app.getString("updatecheck.manifest.version");
		String packageURL = baseURL + packageName;
		if(logger.isDebugEnabled())
			logger.debug("Widevine CDM: version={}, url={}", packageVersion, packageURL);
		if(logger.isDebugEnabled())
			logger.debug("Downloading Widevine CDM...");
		Path packagePath = WidevineCDMDownloader.download(packageURL);
		Path output = widevineDirectory().resolve(packageVersion);
		if(logger.isDebugEnabled())
			logger.debug("Extracting Widevine CDM...");
		WidevineCDMExtractor.extract(packagePath, output);
		if(logger.isDebugEnabled())
			logger.debug("Deleting Widevine CDM .crx file...");
		NIO.deleteFile(packagePath);
		if(logger.isDebugEnabled())
			logger.debug("Widevine CDM downloaded: path={}", output.toAbsolutePath());
	}
	
	public static final Path widevineDirectory() {
		checkOS();
		return Path.of(System.getenv("LOCALAPPDATA"), "CEF/User Data/WidevineCdm");
	}
	
	private static final Stream<Path> streamOfVersions(Path dir) throws IOException {
		return Files.list(dir)
					.map((ver) -> {
						String name = ver.getFileName().toString();
						if(!PATTERN_VERSION.matcher(name).matches())
							return null;
						String[] parts = name.split("\\.");
						long val = 0L;
						val = val * 10000L + Integer.valueOf(parts[0]);
						val = val * 10000L + Integer.valueOf(parts[1]);
						val = val * 10000L + Integer.valueOf(parts[2]);
						val = val * 10000L + Integer.valueOf(parts[3]);
						return new Pair<>(ver, val);
					})
					.filter(Objects::nonNull)
					.sorted((a, b) -> Long.compare(b.b, a.b))
					.map((p) -> p.a);
	}
	
	public static final Path path() throws IOException {
		checkOS();
		Path widevineDir = widevineDirectory();
		if(!NIO.exists(widevineDir)) return null; // Parent directory does not exists, nothing to do
		return streamOfVersions(widevineDir)
				.map((ver) -> {
					Path pathManifest = ver.resolve("manifest.json");
					if(!NIO.exists(pathManifest)) return null;
					SSDCollection data = SSDF.readJSON(pathManifest.toFile());
					return Utils.stream(data.getDirectCollection("platforms").collectionsIterable())
							    .filter((coll) -> coll.getDirectString("os")  .equalsIgnoreCase("win")
							                   && coll.getDirectString("arch").equalsIgnoreCase("x64"))
							    .map((coll) -> ver.resolve(coll.getDirectString("sub_package_path")))
							    .findFirst().orElse(null);
				})
				.filter(Objects::nonNull)
				.map((dir) -> dir.resolve("widevinecdm.dll").toAbsolutePath())
				.filter(NIO::exists)
				.findFirst().orElse(null);
	}
	
	public static final boolean isInstalled() throws IOException {
		return path() != null;
	}
	
	private static final class WidevineCDMDownloadRequest {
		
		private static final String content() {
			String osName = OSUtils.getSystemName();
			String osArch = OSUtils.getSystemArch();
			String osArchNacl = "x86-" + osArch;
			String osArchLong = "x86_" + osArch;
			osArch = 'x' + osArch;
			String osNameLong = OSUtils.isWindows() ? "Windows" : "Unknown";
			String osVersion = System.getProperty("os.version");
			String cefVersion = CefApp.getInstance().getVersion().getChromeVersion();
			Map<String, Object> args = Map.of(
	            "os_name", osName,
	            "os_arch", osArch,
	            "os_arch_nacl", osArchNacl,
	            "os_arch_long", osArchLong,
	            "os_name_long", osNameLong,
	            "os_version", osVersion,
	            "cef_version", cefVersion
	        );
			try(InputStream stream = IntegrationUtils.resourceStream("/resources/drm/widevine-request.json")) {
				String template = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
				return Utils.format(template, args);
			} catch(IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}
		
		public static final SSDCollection send(String url) throws Exception {
			if(context != null)
				context.eventRegistry().call(WidevineCDMEvent.BEGIN_REQUEST);
			StringResponse response = Web.request(new PostRequest(Utils.url(url), UserAgent.CHROME, null).toBodyRequest(content()));
			String content = response.content;
			int index = content.indexOf('{');
			if(index >= 0) content = content.substring(index);
			SSDCollection json = SSDF.readJSON(content);
			if(context != null)
				context.eventRegistry().call(WidevineCDMEvent.END_REQUEST);
			return json;
		}
	}
	
	private static final class WidevineCDMDownloader {
		
		private final TrackerManager manager;
		private DownloadTracker tracker;
		
		private FileChannel channel;
		private StreamResponse response;
		private ByteBuffer buffer;
		
		private long bytes;
		private long total;
		
		private final AtomicBoolean running = new AtomicBoolean();
		
		private WidevineCDMDownloader(TrackerManager manager) {
			this.manager = manager;
		}
		
		private final ReadableByteChannel urlChannel(Request request) throws Exception {
			response = Web.requestStream(request);
			List<String> lengths = Optional.ofNullable(response.headers.get("Content-Length"))
					.orElseGet(() -> response.headers.get("content-length"));
			total = !lengths.isEmpty() ? Long.valueOf(lengths.get(0)) : -1L;
			tracker = new DownloadTracker(total);
			manager.setTracker(tracker);
			return Channels.newChannel(response.stream);
		}
		
		private final void openFile(Path file) throws IOException {
			channel = FileChannel.open(file, CREATE, WRITE);
		}
		
		private final void closeFile() throws Exception {
			if(channel != null) {
				channel.close();
				channel = null;
			}
		}
		
		private final ByteBuffer buffer() {
			return buffer == null ? (buffer = ByteBuffer.allocate(8192)) : (ByteBuffer) buffer.clear();
		}
		
		private final void write(ByteBuffer buffer) throws IOException {
			channel.write(buffer);
		}
		
		private final long download(Request request, Path file) throws Exception {
			running.set(true);
			try(ReadableByteChannel input = urlChannel(request)) {
				openFile(file);
				ByteBuffer buffer = buffer();
				while(true) {
					// Terminate the transfer when not running
					if(!running.get()) {
						input.close();
						// Do not continue
						break;
					}
					int read = input.read(buffer);
					if((read <= 0L)) {
						break;
					}
					// Write the buffer to the output
					buffer.flip();
					write(buffer);
					buffer.clear();
					// Update the bytes
					bytes += read;
					// Update the tracker
					tracker.update(read);
				}
				return bytes;
			} finally {
				// Also close the stream response
				if((response != null)) response.close();
				// Close the opened file
				closeFile();
				running.set(false);
			}
		}
		
		public static final Path download(String packageURL) throws Exception {
			if(context != null)
				context.eventRegistry().call(WidevineCDMEvent.BEGIN_DOWNLOAD, context);
			Path output = PathSystem.getPath(WidevineCDM.class, "widevine.crx");
			TrackerManager manager = context.trackerManager();
			WidevineCDMDownloader downloader = new WidevineCDMDownloader(manager);
			manager.setUpdateListener(() -> {
				if(context != null)
					context.eventRegistry().call(WidevineCDMEvent.UPDATE_DOWNLOAD, new Pair<>(context, manager));
			});
			Request request = new GetRequest(Utils.url(packageURL), UserAgent.CHROME);
			downloader.download(request, output);
			if(context != null)
				context.eventRegistry().call(WidevineCDMEvent.END_DOWNLOAD, context);
			return output;
		}
	}
	
	private static final class WidevineCDMExtractor {
		
		public static final void extract(Path input, Path output) throws Exception {
			if(context != null)
				context.eventRegistry().call(WidevineCDMEvent.BEGIN_EXTRACT, context);
			(new CRXExtractor(input)).extractNoCheck(output);
			if(context != null)
				context.eventRegistry().call(WidevineCDMEvent.END_EXTRACT, context);
		}
	}
	
	public static final class WidevineCDMDownloadReader implements Consumer<String> {

		private static final Pattern PATTERN = Pattern.compile("^\\[[^\\]]+\\]\\s+([^:]+):\\s+(.*)$");
		private static final String TEXT_REQUEST = "Request completed from url";
		
		private final DRMContext context;
		private final AtomicReference<Exception> exception = new AtomicReference<>();
		private final StateMutex mtxDownloaded = new StateMutex();
		
		public WidevineCDMDownloadReader(DRMContext context) {
			this.context = context;
		}
		
		private final void downloadRequest(String requestURL) {
			(new Thread(() -> {
				try {
					download(context, requestURL);
				} catch(Exception ex) {
					exception.set(ex);
				} finally {
					mtxDownloaded.unlock();
				}
			})).start();
		}
		
		@Override
		public void accept(String line) {
			if(line.contains(TEXT_REQUEST)) {
				Matcher matcher = PATTERN.matcher(line);
				if(matcher.matches() && matcher.group(1).equals(TEXT_REQUEST)) {
					String requestURL = matcher.group(2);
					if(logger.isDebugEnabled())
						logger.debug("Widevine CDM request URL: {}", requestURL);
					try {
						downloadRequest(requestURL);
					} catch(Exception ex) {
						exception.set(ex);
					} finally {
						if(CEFLog.instance() != null) {
							try {
								CEFLog.instance().stop();
							} catch(IOException ex) {
								exception.set(ex);
							}
						}
					}
				}
			}
		}
		
		public void awaitDownloaded() {
			mtxDownloaded.await();
		}
		
		public Exception exception() {
			return exception.get();
		}
	}
}