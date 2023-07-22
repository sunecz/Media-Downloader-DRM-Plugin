package sune.app.mediadown.drm.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import sune.api.process.Processes;
import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.net.Web.Request;
import sune.app.mediadown.util.JSON.JSONCollection;
import sune.app.mediadown.util.NIO;
import sune.app.mediadown.util.OSUtils;
import sune.app.mediadown.util.Regex;
import sune.app.mediadown.util.Utils;

/**
 * Used for communication with the custom-made utility WV, that is used for obtaining
 * Widevine decryption keys.
 * @author Sune
 */
public final class WV {
	
	private static Path path;
	
	// Forbid anyone to create an instance of this class
	private WV() {
	}
	
	private static final Path ensureBinary() {
		if(path == null) {
			path = NIO.localPath("resources/binary/drm", OSUtils.getExecutableName("wv"));
			
			if(!NIO.isRegularFile(path)) {
				throw new IllegalStateException("WV utility was not found at " + path.toAbsolutePath().toString());
			}
		}
		
		return path;
	}
	
	public static final Path path() {
		return ensureBinary();
	}
	
	public static final ReadOnlyProcess createSynchronousProcess() {
		return Processes.createSynchronous(path());
	}
	
	public static final ReadOnlyProcess createAsynchronousProcess(Consumer<String> listener) {
		return Processes.createAsynchronous(path(), listener);
	}
	
	public static final List<MediaDecryptionKey> decryptionKeys(MediaDecryptionRequest request) throws Exception {
		MediaDecryptionKeysParser parser = new MediaDecryptionKeysParser();
		
		try(ReadOnlyProcess process = createAsynchronousProcess(parser)) {
			Request req = request.request();
			
			JSONCollection headers = JSONCollection.empty();
			headers.set("User-Agent", req.userAgent());
			headers.set("Pragma", "no-cache");
			headers.set("Cache-Control", "no-cache");
			
			for(Entry<String, List<String>> entry : req.headers().entrySet()) {
				headers.set(entry.getKey(), entry.getValue().get(0));
			}
			
			JSONCollection args = JSONCollection.empty();
			args.set("licenseUrl", req.uri().toString());
			args.set("pssh", request.pssh());
			args.set("headers", headers);
			
			String encoded = Utils.base64Encode(args.toString(true));
			String command = encoded;
			
			process.execute(command);
			process.waitFor();
		}
		
		return parser.decryptionKeys();
	}
	
	private static final class MediaDecryptionKeysParser implements Consumer<String> {
		
		private static final Regex regex = Regex.of("^CONTENT:(?<kid>[^:]+):(?<key>[^:]+)$");
		
		private final List<MediaDecryptionKey> decryptionKeys = new ArrayList<>();
		
		@Override
		public void accept(String line) {
			Matcher matcher;
			if(line == null || !(matcher = regex.matcher(line)).matches()) {
				return;
			}
			
			String kid = matcher.group("kid");
			String key = matcher.group("key");
			decryptionKeys.add(new MediaDecryptionKey(kid, key));
		}
		
		public List<MediaDecryptionKey> decryptionKeys() {
			return decryptionKeys;
		}
	}
}