package sune.app.mediadownloader.drm.util;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

import sune.app.mediadown.util.Pair;
import sune.app.mediadown.util.Reflection;
import sune.app.mediadown.util.Threads;
import sune.util.ssdf2.SSDCollection;
import sune.util.ssdf2.SSDNode;

public final class DRMUtils {
	
	private static final double EPSILON = 0.000001;
	
	// Forbid anyone to create an instance of this class
	private DRMUtils() {
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
	
	public static final class Point2D {
		
		private final int x, y;
		
		public Point2D(int x, int y) {
			this.x = x;
			this.y = y;
		}
		
		public int x() { return x; }
		public int y() { return y; }
	}
	
	public static final class BBox {
		
		private final int x, y, width, height;
		
		public BBox(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
		
		public BBox(SSDCollection bbox) {
			this.x = (int) bbox.getDirectDouble("x");
			this.y = (int) bbox.getDirectDouble("y");
			this.width = (int) bbox.getDirectDouble("width");
			this.height = (int) bbox.getDirectDouble("height");
		}
		
		public Point2D center() {
			return new Point2D(x + width / 2, y + height / 2);
		}
		
		public int x()      { return x;      }
		public int y()      { return y;      }
		public int width()  { return width;  }
		public int height() { return height; }
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
		
		private static MethodHandle mh_sendMouseEvent;
		private static MethodHandle getMethod_sendMouseEvent() throws IllegalStateException {
			if(mh_sendMouseEvent == null) {
				try {
					Method method_sendMouseEvent = clazz.getDeclaredMethod("sendMouseEvent", MouseEvent.class);
					Reflection.setAccessible(method_sendMouseEvent, true);
					mh_sendMouseEvent = MethodHandles.lookup().unreflect(method_sendMouseEvent);
				} catch(NoSuchMethodException
							| NoSuchFieldException
							| IllegalArgumentException
							| IllegalAccessException
							| SecurityException ex) {
					throw new IllegalStateException("Unable to send mouse events", ex);
				}
			}
			return mh_sendMouseEvent;
		}
		
		private final CefBrowser browser;
		
		public BrowserAccessor(CefBrowser browser) {
			this.browser = Objects.requireNonNull(browser);
		}
		
		public final void sendMouseEvent(MouseEvent e) {
			try {
				getMethod_sendMouseEvent().invoke(browser, e);
			} catch(Throwable ex) {
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
		
		public final void click(Point2D point) {
			click(point.x(), point.y());
		}
	}
	
	public static final class JSRequest {
		
		private final Object mtx = new Object();
		private String requestName;
		private String jsCode;
		private SSDNode[] results;
		private int resultIndex;
		private Consumer<SSDNode>[] callbacks;
		
		private Thread thread;
		private final StateMutex mtxDone = new StateMutex();
		private volatile boolean waiting;
		
		@SafeVarargs
		public JSRequest(String requestName, String jsCode, Consumer<SSDNode>... callbacks) {
			this.requestName = Objects.requireNonNull(requestName);
			this.jsCode = jsCode;
			@SuppressWarnings("unchecked")
			Consumer<SSDNode>[] _callbacks = Stream.of(callbacks).filter(Objects::nonNull).toArray(Consumer[]::new);
			this.results = new SSDNode[_callbacks.length];
			this.callbacks = _callbacks;
		}
		
		@SafeVarargs
		public static final JSRequest ofNoop(String requestName, Consumer<SSDNode>... callbacks) {
			return new JSRequest(requestName, null, callbacks);
		}
		
		@SafeVarargs
		public static final JSRequest of(String requestName, String jsCode, Consumer<SSDNode>... callbacks) {
			return new JSRequest(requestName, Objects.requireNonNull(jsCode), callbacks);
		}
		
		public JSRequest send(CefFrame frame) {
			if(jsCode == null) return this; // Noop
			
			// If already sent and is waiting, terminate it first
			interrupt();
			
			String codeQuery = "window.cefQuery({request:'" + requestName + ".'+i+':'+JSON.stringify({'data':d})});";
			String code = "(new Promise((_rs,_rj)=>{const ret=function(i,d){" + codeQuery + "_rs(0)};" + jsCode + "}))";
			frame.executeJavaScript(code, null, 0);
			
			(thread = Threads.newThreadUnmanaged(() -> {
				synchronized(mtx) {
					try {
						waiting = true;
						mtx.wait();
						callback(resultIndex);
					} catch(InterruptedException ex) {
						// Ignore, nothing we can do
					} finally {
						waiting = false;
						mtxDone.unlock();
					}
				}
			})).start();
			
			return this;
		}
		
		private final void callback(int index) {
			callbacks[index].accept(results[index]);
		}
		
		public void resolve(int index, SSDNode result) {
			synchronized(mtx) {
				this.results[index] = result;
				this.resultIndex = index;
				mtx.notifyAll();
				if(!waiting) callback(resultIndex);
			}
		}
		
		public void interrupt() {
			if(waiting) {
				thread.interrupt();
				mtxDone.await();
			}
		}
		
		public String requestName() {
			return requestName;
		}
		
		public SSDNode[] results() {
			return results;
		}
	}
	
	private static abstract class PromiseBase<T> implements Promise<T> {
		
		private static final byte STATE_RESOLVED = 1;
		private static final byte STATE_REJECTED = 2;
		
		private final    CompletableFuture<Pair<Byte, T>> first;
		private volatile CompletableFuture<Pair<Byte, T>> next;
		
		public PromiseBase() {
			first = new CompletableFuture<>();
			next  = first;
		}
		
		@Override
		public void resolve(T arg) {
			first.complete(new Pair<>(STATE_RESOLVED, arg));
		}
		
		@Override
		public void reject(T arg) {
			first.complete(new Pair<>(STATE_REJECTED, arg));
		}
		
		@Override
		public PromiseBase<T> then(Consumer<T> resolved, Consumer<T> rejected) {
			next = next.handleAsync((p, t) -> {
				switch(p.a) {
					case STATE_RESOLVED:
						if(resolved != null)
							resolved.accept(p.b);
						break;
					case STATE_REJECTED:
						if(rejected != null)
							rejected.accept(p.b);
						break;
				}
				
				return p;
			});
			
			return this;
		}
		
		@Override
		public PromiseBase<T> then(Consumer<T> resolved) {
			return then(resolved, null);
		}
		
		@Override
		public T get() throws InterruptedException, ExecutionException {
			Pair<Byte, T> result = first.get();
			return result != null ? result.b : null;
		}
		
		@Override
		public void await() throws InterruptedException, ExecutionException {
			get();
		}
	}
	
	public static interface Promise<T> {
		
		void resolve(T arg);
		void reject(T arg);
		Promise<T> then(Consumer<T> resolved, Consumer<T> rejected);
		Promise<T> then(Consumer<T> resolved);
		T get() throws InterruptedException, ExecutionException;
		void await() throws InterruptedException, ExecutionException;
		
		public static final class OfRef<T> extends PromiseBase<T> {
			
			@Override
			public OfRef<T> then(Consumer<T> resolved, Consumer<T> rejected) {
				return (OfRef<T>) super.then(resolved, rejected);
			}
			
			@Override
			public OfRef<T> then(Consumer<T> resolved) {
				return (OfRef<T>) super.then(resolved);
			}
		}
		
		public static final class OfVoid extends PromiseBase<Void> {
			
			private static final Consumer<Void> consumer(Runnable runnable) {
				return ((v) -> runnable.run());
			}
			
			public void resolve() { resolve(null); }
			public void reject()  { reject (null); }
			
			@Override
			public OfVoid then(Consumer<Void> resolved, Consumer<Void> rejected) {
				return (OfVoid) super.then(resolved, rejected);
			}
			
			@Override
			public OfVoid then(Consumer<Void> resolved) {
				return (OfVoid) super.then(resolved);
			}
			
			public OfVoid then(Runnable resolved, Runnable rejected) {
				return (OfVoid) super.then(consumer(resolved), consumer(rejected));
			}
			
			public OfVoid then(Runnable resolved) {
				return (OfVoid) super.then(consumer(resolved));
			}
		}
	}
}