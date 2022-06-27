package sune.app.mediadownloader.drm.util;

import java.util.Arrays;

public final class RecordMetrics {
	
	private static final double SECS_IN_NANO = 1e-9;
	
	private static final int MEMORY_CAPACITY = 128;
	private static final int FPS_MIN = 10;
	private static final int FPS_MAX = 90;
	
	private final FPSCalculator fpsCalc = new FPSCalculator(MEMORY_CAPACITY);
	private final MemoryHistogram histogram = new MemoryHistogram(FPS_MIN, FPS_MAX, MEMORY_CAPACITY);
	
	private double recordTime;
	
	private int lastPlaybackFrames;
	private double lastPlaybackTime;
	private long lastUpdateRecordTime;
	private double lastRecordTime;
	
	public RecordMetrics() {
		reset();
	}
	
	private final double recordTime(long now) {
		return recordTime + (now - lastUpdateRecordTime) * SECS_IN_NANO;
	}
	
	private final int histogramFPS(double fps) {
		return Math.max(FPS_MIN, Math.min((int) fps, FPS_MAX));
	}
	
	public final void updatePlayback(double time, int frames) {
		double dt = time - lastPlaybackTime;
		int df = frames - lastPlaybackFrames;
		if(dt == 0.0) return; // No update
		double delta = df * (1.0 / dt);
		fpsCalc.add(delta);
		histogram.add(histogramFPS(fpsCalc.get()));
		lastPlaybackFrames = frames;
		lastPlaybackTime = time;
	}
	
	public final double recordTime() { // Self-synchronizing record time
		return recordTime(System.nanoTime());
	}
	
	public final void updateRecord(double time, int frames, double fps) {
		recordTime = time;
		if(!DRMUtils.eq(recordTime, lastRecordTime)) {
			lastUpdateRecordTime = System.nanoTime();
		}
		lastRecordTime = time;
	}
	
	public final void reset() {
		lastPlaybackFrames = 0;
		lastPlaybackTime = 0.0;
		lastUpdateRecordTime = System.nanoTime();
		fpsCalc.reset();
		histogram.reset();
		
	}
	
	public final double startCutOff() {
		return recordTime();
	}
	
	public final int playbackFPS() {
		return histogram.average();
	}
	
	private static final class FPSCalculator {
		
		private int memoryIdxR = 0;
		private int memoryIdxW = 0;
		private int memoryLen = 0;
		private final int memoryCap;
		private final double[] memory;
		private double memorySum = 0.0;
		private double fps = 0.0;
		
		public FPSCalculator(int capacity) {
			this.memoryCap = capacity;
			this.memory = new double[capacity];
		}
		
		public final void add(double delta) {
			double value = 0.0;
			if(memoryLen == memoryCap) {
				value = memory[memoryIdxR];
				memoryIdxR = (++memoryIdxR) % memoryCap;
			} else ++memoryLen;
			memory[memoryIdxW] = delta;
			memoryIdxW = (++memoryIdxW) % memoryCap;
			memorySum += delta - value;
			fps = memorySum / memoryLen;
		}
		
		public final void reset() {
			fps = 0.0;
			memoryIdxR = 0;
			memoryIdxW = 0;
			memoryLen = 0;
			memorySum = 0.0;
			Arrays.fill(memory, 0.0);
		}
		
		public final double get() {
			return fps;
		}
	}
	
	private static final class MemoryHistogram {
		
		private final int min;
		private final int[] histogram;
		private int memoryIdxR = 0;
		private int memoryIdxW = 0;
		private final int[] memory;
		private final int memoryCap;
		private int memoryLen = 0;
		
		public MemoryHistogram(int min, int max, int capacity) {
			this.min = min;
			this.histogram = new int[max - min + 1];
			this.memoryCap = capacity;
			this.memory = new int[capacity];
		}
		
		public final void add(int value) {
			int histValue = value - min;
			if(memoryLen == memoryCap) {
				--histogram[memory[memoryIdxR]];
				memoryIdxR = (++memoryIdxR) % memoryCap;
			} else ++memoryLen;
			memory[memoryIdxW] = histValue;
			memoryIdxW = (++memoryIdxW) % memoryCap;
			++histogram[histValue];
		}
		
		public final int average() {
			long sumv = 0L, sumw = 0L;
			for(int i = 0, l = histogram.length; i < l; ++i) {
				sumv += (i + min) * histogram[i];
				sumw += histogram[i];
			}
			return (int) (sumv / sumw);
		}
		
		public final void reset() {
			memoryIdxR = 0;
			memoryIdxW = 0;
			memoryLen = 0;
			Arrays.fill(histogram, 0);
			Arrays.fill(memory, 0);
		}
	}
}