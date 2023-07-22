package sune.app.mediadown.drm.util;

import java.util.Objects;

import sune.app.mediadown.net.Web.Request;

public final class MediaDecryptionRequest {
	
	private final String pssh;
	private final Request request;
	
	public MediaDecryptionRequest(String pssh, Request request) {
		this.pssh = Objects.requireNonNull(pssh);
		this.request = Objects.requireNonNull(request);
	}
	
	public String pssh() {
		return pssh;
	}
	
	public Request request() {
		return request;
	}
}