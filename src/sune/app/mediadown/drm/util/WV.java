package sune.app.mediadown.drm.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import sune.app.mediadown.net.Net;
import sune.app.mediadown.net.Web;
import sune.app.mediadown.net.Web.Request;
import sune.app.mediadown.net.Web.Response;
import sune.app.mediadown.util.JSON;
import sune.app.mediadown.util.JSON.JSONCollection;
import sune.app.mediadown.util.JSON.JSONObject;
import sune.app.mediadown.util.Utils;

/**
 * Used for communication with the custom-made utility WV, that is used for obtaining
 * Widevine decryption keys.
 * @author Sune
 */
public final class WV {
	
	// Forbid anyone to create an instance of this class
	private WV() {
	}
	
	public static final class API {
		
		private static final URI URI_API = Net.uri("https://md.sune.app/api/wv/v1/");
		
		private API() {
		}
		
		private static final JSONCollection request(String path, JSONCollection body) throws Exception {
			try(Response.OfStream response = Web.requestStream(
					Request.of(URI_API.resolve(path))
						.POST(body.toString(true), "application/json")
			)) {
				return JSON.read(response.stream());
			}
		}
		
		public static final LicenseRequest generateLicenseRequest(String pssh) throws Exception {
			JSONCollection response = request(
				"generate",
				JSONCollection.ofObject(
					"pssh", JSONObject.ofString(pssh)
				)
			);
			
			String id = response.getString("id");
			byte[] request = Utils.base64DecodeRaw(response.getString("request"));
			
			return new LicenseRequest(id, request);
		}
		
		public static final List<LicenseKey> extractLicenseKeys(String licenseId, byte[] licenseResponse)
				throws Exception {
			JSONCollection response = request(
				"extract",
				JSONCollection.ofObject(
					"id", JSONObject.ofString(licenseId),
					"response", JSONObject.ofString(Utils.base64EncodeRawAsString(licenseResponse))
				)
			);
			
			JSONCollection rawKeys = response.getCollection("keys");
			int length;
			
			if(rawKeys == null || (length = rawKeys.length()) == 0) {
				return List.of();
			}
			
			List<LicenseKey> keys = new ArrayList<>(length);
			
			for(JSONCollection item : rawKeys.collectionsIterable()) {
				keys.add(new LicenseKey(
					item.getString("type"),
					item.getString("kid"),
					item.getString("key")
				));
			}
			
			return Collections.unmodifiableList(keys);
		}
		
		public static final class LicenseRequest {
			
			private final String id;
			private final byte[] request;
			
			private LicenseRequest(String id, byte[] request) {
				this.id = Objects.requireNonNull(id);
				this.request = Objects.requireNonNull(request);
			}
			
			public boolean isValid() { return id != null && request != null; }
			public String id() { return id; }
			public byte[] request() { return request; }
		}
		
		public static final class LicenseKey {
			
			private final String type;
			private final String kid;
			private final String key;
			
			private LicenseKey(String type, String kid, String key) {
				this.type = Objects.requireNonNull(type);
				this.kid = Objects.requireNonNull(kid);
				this.key = Objects.requireNonNull(key);
			}
			
			public String type() { return type; }
			public String kid() { return kid; }
			public String key() { return key; }
		}
	}
}