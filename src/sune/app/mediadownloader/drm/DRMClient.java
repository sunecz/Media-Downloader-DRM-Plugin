package sune.app.mediadownloader.drm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.network.CefRequest.TransitionType;

import sune.app.mediadownloader.drm.util.DRMUtils.JSRequest;
import sune.app.mediadownloader.drm.util.StateMutex;
import sune.util.ssdf2.SSDCollection;
import sune.util.ssdf2.SSDF;

public class DRMClient {
	
	private static final String URL_BLANK = "about:blank";
	
	private final CefClient client;
	private final DRMContext context;
	private final DRMProxy proxy;
	private final DRMResolver resolver;
	private DRMBrowser browser;
	private LoadNotifier loadNotifier;
	
	private final Map<String, JSRequest> jsResults = new HashMap<>();
	
	public DRMClient(CefClient client, DRMContext context, DRMProxy proxy, DRMResolver resolver) {
		this.client = client;
		this.context = context;
		this.proxy = proxy;
		this.resolver = resolver;
		this.loadNotifier = new LoadNotifier();
	}
	
	public DRMBrowser createBrowser(int width, int height) {
		if(browser != null)
			throw new IllegalStateException("Browser already created");
		CefMessageRouter msgRouter = CefMessageRouter.create();
		msgRouter.addHandler(new CefMessageRouterHandlerAdapter() {
			
			@Override
			public boolean onQuery(CefBrowser b, CefFrame frame, long query_id, String request,
					boolean persistent, CefQueryCallback callback) {
				String requestNameData = request.substring(0, request.indexOf(':') + 1), requestName = requestNameData;
				int delim = requestNameData.indexOf('.'), index = 0;
				if(delim > 0) {
					requestName = requestNameData.substring(0, delim) + ':';
					index = Integer.valueOf(requestNameData.substring(delim + 1, requestNameData.length() - 1));
				}
				String value = request.substring(requestNameData.length());
				SSDCollection json = SSDF.readJSON(value);
				boolean handled = false;
				JSRequest result = jsResults.get(requestName);
				if(result != null) {
					result.setResult(index, json.getDirect("data"));
					handled = true;
				}
				requestName = requestName.substring(0, requestName.length() - 1);
				resolver.onRequest(browser, frame, requestName, json, request);
				return handled;
			}
		}, true);
		client.addMessageRouter(msgRouter);
		client.addLoadHandler(new CefLoadHandlerAdapter() {
			
			@Override
			public void onLoadStart(CefBrowser b, CefFrame frame, TransitionType transitionType) {
				resolver.onLoadStart(browser, frame);
			}
			
			@Override
			public void onLoadEnd(CefBrowser b, CefFrame frame, int httpStatusCode) {
				loadNotifier.unlock(frame.getURL());
				resolver.onLoadEnd(browser, frame, httpStatusCode);
			}
		});
		client.addDisplayHandler(new CefDisplayHandlerAdapter() {
			
			private final boolean isDebugDisabled = !DRM.isDebug();
			
			@Override
			public boolean onConsoleMessage(CefBrowser browser, org.cef.CefSettings.LogSeverity level, String message, String source, int line) {
				return isDebugDisabled;
			}
		});
		browser = new DRMBrowser(context, this, width, height);
		return browser;
	}
	
	public void awaitLoaded() {
		loadNotifier.await(URL_BLANK);
	}
	
	public void addJSRequest(JSRequest result) {
		jsResults.put(result.requestName() + ':', result);
	}
	
	public CefClient cefClient() {
		return client;
	}
	
	public DRMProxy proxy() {
		return proxy;
	}
	
	public DRMResolver resolver() {
		return resolver;
	}
	
	public LoadNotifier loadNotifier() {
		return loadNotifier;
	}
	
	public final class LoadNotifier {
		
		private final Map<String, StateMutex> mutexes = new HashMap<>();
		private final Set<String> enabled = new HashSet<>();
		private final Object mutex = new Object();
		
		public LoadNotifier() {
			// Make sure that the blank URL is present
			enable(URL_BLANK);
		}
		
		private final StateMutex get(String url) {
			synchronized(mutex) {
				return enabled.contains(url)
							? mutexes.computeIfAbsent(url, (k) -> new StateMutex())
							: null;
			}
		}
		
		public final void enable(String url) {
			synchronized(mutex) {
				enabled.add(url);
			}
		}
		
		public final void disable(String url) {
			// Make sure that the blank URL is always enabled
			if(url.equals(URL_BLANK)) return;
			synchronized(mutex) {
				enabled.remove(url);
				mutexes.remove(url);
			}
		}
		
		public final void await(String url) {
			StateMutex mtx = get(url);
			if(mtx != null) mtx.await();
		}
		
		public final void unlock(String url) {
			StateMutex mtx = get(url);
			if(mtx != null) mtx.unlock();
		}
	}
}