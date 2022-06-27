package sune.app.mediadownloader.drm.util;

import java.util.Locale;

public interface Cut<T> {
	
	T start();
	T end();
	T length();
	
	static class OfDouble implements Cut<Double> {
		
		private final double start, end; // In seconds
		
		public OfDouble(double start, double end) {
			this.start = start;
			this.end = end;
		}
		
		public final Double start() { return start; }
		public final Double end() { return end; }
		public final Double length() { return end - start; }
		
		@Override
		public String toString() {
			return String.format(Locale.US, "%s(%.6f, %.6f, length=%.6f)", getClass().getSimpleName(), start(), end(), length());
		}
	}
	
	static class OfLong implements Cut<Long> {
		
		private final long start, end; // In seconds
		
		public OfLong(long start, long end) {
			this.start = start;
			this.end = end;
		}
		
		public final Long start() { return start; }
		public final Long end() { return end; }
		public final Long length() { return end - start; }
		
		@Override
		public String toString() {
			return String.format(Locale.US, "%s(%d, %d, length=%d)", getClass().getSimpleName(), start(), end(), length());
		}
	}
}