package sune.app.mediadownloader.drm.util;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.StackWalker.StackFrame;
import java.util.Iterator;

public final class JCEF {
	
	private static final class _JCefDisabledPrintStream extends PrintStream {

		public _JCefDisabledPrintStream(OutputStream out) {
			super(out);
		}
		
		private final boolean canProceed() {
			// Find out whether the call was made from the JCEF package or not
			return StackWalker.getInstance().walk((stream) -> {
				Iterator<StackFrame> it = stream.iterator();
				StackFrame frame = null;
				// Find the first PrintStream class call
				boolean found = false;
				while(it.hasNext()) {
					frame = it.next();
					if(frame.getClassName().startsWith("java.io.PrintStream")) {
						found = true;
						break;
					}
				}
				if(found) {
					// Skip to the last PrintStream class call
					while(it.hasNext()) {
						frame = it.next();
						if(!frame.getClassName().startsWith("java.io.PrintStream"))
							break;
					}
					// Check for the JCEF package
					if(frame.getClassName().startsWith("org.cef.")) {
						return false;
					}
				}
				// JCEF package not found
				return true;
			});
		}
		
		@Override
		public void write(int b) {
			if(canProceed())
				super.write(b);
		}
		
		@Override
		public void write(byte[] buf, int off, int len) {
			if(canProceed())
				super.write(buf, off, len);
		}
	}
	
	public static final void disablePrintToConsole() {
		// Do only stdout, since we want errors to be shown
		System.setOut(new _JCefDisabledPrintStream(System.out));
	}
}