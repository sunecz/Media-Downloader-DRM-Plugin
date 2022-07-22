package sune.app.mediadownloader.drm.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class StateMutex {
	
	private final AtomicReference<Throwable> exception = new AtomicReference<>();
	private final AtomicBoolean unlocked = new AtomicBoolean();
	
	public final boolean await() {
		synchronized(this) {
			if(unlocked.get())
				return true;
			try {
				wait();
			} catch(InterruptedException ex) {
				exception.set(ex);
			}
			return exception.get() != null;
		}
	}
	
	public final void unlock() {
		synchronized(this) {
			unlocked.set(true);
			notifyAll();
		}
	}
	
	public final Throwable getException() {
		return exception.get();
	}
	
	public final Throwable getExceptionAndReset() {
		return exception.getAndSet(null);
	}
}