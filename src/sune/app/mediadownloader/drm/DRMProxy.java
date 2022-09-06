package sune.app.mediadownloader.drm;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.mitm.CertificateSniffingMitmManager;
import org.littleshoot.proxy.mitm.RootCertificateException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;

public class DRMProxy {
	
	private static final int BUFFER_SIZE = 16 * 1024 * 1024;
	private static final AttributeKey<String> ATTR_CONNECTED_URL = AttributeKey.valueOf("connected_url");
	private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
	private static final String DEFAULT_CHARSET_NAME = StandardCharsets.UTF_8.name();
	
	private final int port;
	private final DRMResolver resolver;
	private HttpProxyServerBootstrap serverBootstrap;
	private HttpProxyServer server;
	
	DRMProxy(int port, DRMResolver resolver) {
		this.port = port;
		this.resolver = resolver;
	}
	
	private static final HttpHeadersInfo httpHeadersInfo(HttpHeaders headers) {
		String contentType = headers.get("Content-Type");
		String mimeType = DEFAULT_MIME_TYPE;
		String charsetName = DEFAULT_CHARSET_NAME;
		if(contentType != null) {
			String[] parts = contentType.split(";");
			mimeType = parts[0];
			if(parts.length > 1) {
				charsetName = parts[1].stripLeading().replaceAll("^charset=", "");
			}
		}
		return new HttpHeadersInfo(mimeType, charsetName);
	}
	
	private static final FullHttpResponse newSuccessResponse(ByteBuf buf) {
		return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
	}
	
	public DRMProxy create() throws RootCertificateException {
		serverBootstrap = DefaultHttpProxyServer.bootstrap().withPort(port)
			.withManInTheMiddle(new CertificateSniffingMitmManager())
			.withFiltersSource(new DRMHttpFiltersSource(resolver));
		return this;
	}
	
	public void start() {
		if(server != null)
			return; // Already running, nothing to do
		if(serverBootstrap == null)
			throw new IllegalStateException("Proxy not created yet");
		server = serverBootstrap.start();
		serverBootstrap = null;
	}
	
	public void stop() {
		if(server == null)
			return; // Not running, nothing to do
		server.stop();
	}
	
	private static final class HttpHeadersInfo {
		
		private final String mimeType;
		private final Charset charset;
		
		public HttpHeadersInfo(String mimeType, String charsetName) {
			this.mimeType = mimeType;
			Charset charset = StandardCharsets.UTF_8;
			try {
				charset = Charset.forName(charsetName);
			} catch(IllegalCharsetNameException ex) {
				// Ignore
			}
			this.charset = charset;
		}
		
		public String mimeType() {
			return mimeType;
		}
		
		public Charset charset() {
			return charset;
		}
	}
	
	private static final class DRMHttpFiltersSource implements HttpFiltersSource {
		
		private final DRMResolver resolver;
		
		public DRMHttpFiltersSource(DRMResolver resolver) {
			this.resolver = resolver;
		}
		
		@Override
		public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext clientCtx) {
			if(originalRequest.getMethod() != HttpMethod.CONNECT)
				return new DRMHttpFilters(resolver, originalRequest, clientCtx);
			if(clientCtx != null) {
				String prefix = "https://" + originalRequest.getUri().replaceFirst(":443$", "");
				clientCtx.channel().attr(ATTR_CONNECTED_URL).set(prefix);
			}
			return new HttpFiltersAdapter(originalRequest, clientCtx);
		}
		
		@Override
		public int getMaximumResponseBufferSizeInBytes() {
			return BUFFER_SIZE;
		}
		
		@Override
		public int getMaximumRequestBufferSizeInBytes() {
			return BUFFER_SIZE;
		}
	}
	
	private static final class DRMHttpFilters extends HttpFiltersAdapter {
		
		private final DRMResolver resolver;
		private String uri = null;
		
		public DRMHttpFilters(DRMResolver resolver, HttpRequest originalRequest, ChannelHandlerContext clientCtx) {
			super(originalRequest, clientCtx);
			this.resolver = resolver;
		}
		
		@Override
		public HttpResponse proxyToServerRequest(HttpObject httpObject) {
			if(httpObject instanceof FullHttpRequest) {
				FullHttpRequest request = (FullHttpRequest) httpObject;
				if(resolver.shouldModifyRequest(request)) {
					resolver.modifyRequest(request);
				}
			}
			return null;
		}
		
		@Override
		public HttpResponse clientToProxyRequest(HttpObject httpObject) {
			if(httpObject instanceof FullHttpRequest) {
				FullHttpRequest request = (FullHttpRequest) httpObject;
				uri = ctx.channel().attr(ATTR_CONNECTED_URL).get() + request.getUri();
			}
			return null;
		}
		
		@Override
		public HttpObject proxyToClientResponse(HttpObject httpObject) {
			if(httpObject instanceof FullHttpResponse) {
				FullHttpResponse response = (FullHttpResponse) httpObject;
				HttpHeadersInfo info = httpHeadersInfo(response.headers());
				String mimeType = info.mimeType();
				Charset charset = info.charset();
				if(resolver.shouldModifyResponse(uri, mimeType, charset)) {
					ByteBuf buf = response.content();
					byte[] bytes = new byte[buf.readableBytes()];
					buf.getBytes(buf.readerIndex(), bytes);
					String content = new String(bytes, charset);
					String newContent = resolver.modifyResponse(uri, mimeType, charset, content);
					byte[] newBytes = newContent.getBytes(charset);
					ByteBuf newBuf = Unpooled.buffer(newBytes.length);
					newBuf.writeBytes(newBytes, 0, newBytes.length);
					FullHttpResponse newResponse = newSuccessResponse(newBuf);
					newResponse.headers().set(response.headers());
					HttpHeaders.setContentLength(newResponse, newBuf.readableBytes());
					httpObject = newResponse;
				}
				uri = null;
			}
			return httpObject;
		}
	}
}