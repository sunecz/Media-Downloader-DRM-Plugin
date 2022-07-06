package sune.app.mediadownloader.drm.util;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

import sune.app.mediadown.media.MediaQuality;
import sune.app.mediadown.media.MediaType;
import sune.app.mediadown.util.Reflection;
import sune.app.mediadownloader.drm.integration.IntegrationUtils;
import sune.util.ssdf2.SSDCollection;
import sune.util.ssdf2.SSDNode;

public final class DRMUtils {
	
	private static final double EPSILON = 0.000001;
	
	// Forbid anyone to create an instance of this class
	private DRMUtils() {
	}
	
	public static final class BrowserAccessor {
		
		private static final Class<?> clazz;
		
		static {
			Class<?> _clazz = null;
			try {
				_clazz = Class.forName("org.cef.browser.CefBrowser_N");
			} catch(ClassNotFoundException ex) {
				throw new IllegalStateException("Unable to initialize browser accessor.");
			}
			clazz = _clazz;
		}
		
		private static Method method_sendMouseEvent;
		private static Method getMethod_sendMouseEvent() throws IllegalStateException {
			if(method_sendMouseEvent == null) {
				try {
					method_sendMouseEvent = clazz.getDeclaredMethod("sendMouseEvent", MouseEvent.class);
					Reflection.setAccessible(method_sendMouseEvent, true);
				} catch(NoSuchMethodException
							| NoSuchFieldException
							| IllegalArgumentException
							| IllegalAccessException
							| SecurityException ex) {
					throw new IllegalStateException("Unable to send mouse events", ex);
				}
			}
			return method_sendMouseEvent;
		}
		
		private final CefBrowser browser;
		
		public BrowserAccessor(CefBrowser browser) {
			this.browser = browser;
		}
		
		public final void sendMouseEvent(MouseEvent e) {
			try {
				getMethod_sendMouseEvent().invoke(browser, e);
			} catch(IllegalAccessException
						| IllegalArgumentException
						| InvocationTargetException
						| IllegalStateException ex) {
				throw new IllegalStateException("Unable to send mouse event", ex);
			}
		}
		
		@SuppressWarnings("deprecation")
		public final void click(int x, int y) {
			Component component = browser.getUIComponent();
			sendMouseEvent(new MouseEvent(component, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false));
			sendMouseEvent(new MouseEvent(component, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_MASK, x, y, 1, false));
			sendMouseEvent(new MouseEvent(component, MouseEvent.MOUSE_RELEASED, 0, MouseEvent.BUTTON1_MASK, x, y, 1, false));
		}
	}
	
	public static final class JSRequest {
		
		private final Object mtx = new Object();
		private String requestName;
		private String jsCode;
		private SSDNode[] results;
		private int resultIndex;
		private Consumer<SSDNode>[] callbacks;
		private boolean waiting;
		
		@SafeVarargs
		public JSRequest(String requestName, String jsCode, Consumer<SSDNode>... callbacks) {
			this.requestName = Objects.requireNonNull(requestName);
			this.jsCode = Objects.requireNonNull(jsCode);
			@SuppressWarnings("unchecked")
			Consumer<SSDNode>[] _callbacks = Stream.of(callbacks).filter((c) -> c != null).toArray(Consumer[]::new);
			this.results = new SSDNode[_callbacks.length];
			this.callbacks = _callbacks;
		}
		
		public JSRequest send(CefFrame frame) {
			String codeQuery = "window.cefQuery({request:'" + requestName + ".'+i+':'+JSON.stringify({'data':d})});";
			String code = "(new Promise((_rs,_rj)=>{const ret=function(i,d){" + codeQuery + "_rs(0)};" + jsCode + "}))";
			frame.executeJavaScript(code, null, 0);
			(new Thread(() -> {
				synchronized(mtx) {
					try {
						waiting = true;
						mtx.wait();
						callback(resultIndex);
					} catch(InterruptedException ex) {
						// Ignore, nothing we can do
					} finally {
						waiting = false;
					}
				}
			})).start();
			return this;
		}
		
		private final void callback(int index) {
			callbacks[index].accept(results[index]);
		}
		
		public void setResult(int index, SSDNode result) {
			synchronized(mtx) {
				this.results[index] = result;
				this.resultIndex = index;
				mtx.notifyAll();
				if(!waiting) callback(resultIndex);
			}
		}
		
		public String requestName() {
			return requestName;
		}
		
		public SSDNode[] results() {
			return results;
		}
	}
	
	// Bit rates taken from https://en.wikipedia.org/wiki/Bit_rate#MP3
	public static enum AudioQuality {
		
		// The order is important!
		UNKNOWN(true),
		LOW    (96.0),
		MEDIUM (192.0),
		HIGH   ();
		
		private final double maxBitRate; // In kbps
		
		// Special constructor for UNKNOWN quality
		private AudioQuality(boolean isUnknown) {
			this.maxBitRate = -1;
		}
		
		// Special constructor for the highest recognized quality
		private AudioQuality() {
			this.maxBitRate = Integer.MAX_VALUE;
		}
		
		// Normal constructor for other qualities
		private AudioQuality(double maxBitRate) {
			if(maxBitRate <= 0)
				throw new IllegalArgumentException("Invalid maximum bit rate");
			this.maxBitRate = maxBitRate;
		}
		
		public static final AudioQuality fromBitRate(double bitRate) {
			for(AudioQuality quality : values()) {
				if(bitRate <= quality.maxBitRate)
					return quality;
			}
			return AudioQuality.UNKNOWN;
		}
		
		public static final AudioQuality fromMediaQuality(MediaQuality quality) {
			if(quality == MediaQuality.UNKNOWN)
				return UNKNOWN;
			if(quality.compareTo(MediaQuality.fromString("medium", MediaType.AUDIO)) < 0)
				return LOW;
			if(quality.compareTo(MediaQuality.fromString("high", MediaType.AUDIO)) < 0)
				return MEDIUM;
			return HIGH;
		}
		
		public double maxBitRate() {
			return maxBitRate;
		}
		
		public double minBitRate() {
			if(this == UNKNOWN)
				return -1;
			return Math.nextUp(values()[ordinal() - 1].maxBitRate);
		}
	}
	
	public static final class Point2D {
		
		public final int x, y;

		public Point2D(int x, int y) {
			this.x = x;
			this.y = y;
		}
	}
	
	public static final class BBox {
		
		public final int x, y, width, height;

		public BBox(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}
	
	public static final boolean executeJSFile(CefFrame frame, String path) {
		try(InputStream stream = IntegrationUtils.resourceStream(path)) {
			String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			frame.executeJavaScript(content, null, 0);
			return true;
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		return false;
	}
	
	public static final BBox getBBox(SSDCollection bbox) {
		int x = (int) bbox.getDirectDouble("x");
		int y = (int) bbox.getDirectDouble("y");
		int w = (int) bbox.getDirectDouble("width");
		int h = (int) bbox.getDirectDouble("height");
		return new BBox(x, y, w, h);
	}
	
	public static final Point2D getCenter(BBox bbox) {
		return new Point2D(bbox.x + bbox.width / 2, bbox.y + bbox.height / 2);
	}
	
	public static final boolean eq(double a, double b) {
		return eq(a, b, EPSILON);
	}
	
	public static final boolean eq(double a, double b, double epsilon) {
		return Math.abs(a - b) <= epsilon;
	}
	
	public static final boolean lte(double a, double b) {
		return lte(a, b, EPSILON);
	}
	
	public static final boolean lte(double a, double b, double epsilon) {
		return a - b <= epsilon;
	}
	
	public static final boolean gte(double a, double b) {
		return gte(a, b, EPSILON);
	}
	
	public static final boolean gte(double a, double b, double epsilon) {
		return b - a <= epsilon;
	}
	
	public static final String format(String format, Object... args) {
		return String.format(Locale.US, format, args);
	}
	
	public static final String toString(double val) {
		return format("%.6f", val);
	}
}