package sune.app.mediadownloader.drm.util;

import java.util.Arrays;

public final class RecordMetrics {
	
	private static final double SECS_IN_NANO = 1e-9;
	
	private static final int MEMORY_CAPACITY = 128;
	private static final int FRAME_RATE_MIN = 10;
	private static final int FRAME_RATE_MAX = 90;
	
	private final FrameRateCalculator frameRateCalc = new FrameRateCalculator(MEMORY_CAPACITY);
	private final MemoryHistogram histogram = new MemoryHistogram(FRAME_RATE_MIN, FRAME_RATE_MAX, MEMORY_CAPACITY);
	
	private double recordTime;
	
	private int lastPlaybackFrames;
	private double lastPlaybackTime;
	private double lastRecordTime;
	private long lastRecordUpdateTime;
	private double recordTimeAccumulator;
	
	public RecordMetrics() {
		reset();
	}
	
	private final double recordTime(long now) {
		return recordTime + (recordTimeAccumulator + (now - lastRecordUpdateTime)) * SECS_IN_NANO;
	}
	
	private final int histogramFrameRate(double frameRate) {
		return Math.max(FRAME_RATE_MIN, Math.min((int) frameRate, FRAME_RATE_MAX));
	}
	
	public final void updatePlayback(double time, int frames) {
		double dt = time - lastPlaybackTime;
		int df = frames - lastPlaybackFrames;
		if(dt == 0.0) return; // No update
		double delta = df * (1.0 / dt);
		frameRateCalc.add(delta);
		histogram.add(histogramFrameRate(frameRateCalc.get()));
		lastPlaybackFrames = frames;
		lastPlaybackTime = time;
	}
	
	public final double recordTime() {
		return recordTime;
	}
	
	public final double recordTimeNow() { // Self-synchronizing record time
		return recordTime(System.nanoTime());
	}
	
	public final void updateRecord(double time, int frames, double frameRate) {
		long now = System.nanoTime();
		recordTime = time;
		
		if(!DRMUtils.eq(recordTime, lastRecordTime)) {
			recordTimeAccumulator = 0.0;
		} else {
			recordTimeAccumulator += (now - lastRecordUpdateTime);
		}
		
		lastRecordUpdateTime = now;
		lastRecordTime = time;
	}
	
	public final void reset() {
		lastPlaybackFrames = 0;
		lastPlaybackTime = 0.0;
		frameRateCalc.reset();
		histogram.reset();
		
	}
	
	public final int playbackFrameRate() {
		return histogram.average();
	}
	
	private static final class FrameRateCalculator {
		
		private int memoryIdxR = 0;
		private int memoryIdxW = 0;
		private int memoryLen = 0;
		private final int memoryCap;
		private final double[] memory;
		private double memorySum = 0.0;
		private double frameRate = 0.0;
		
		public FrameRateCalculator(int capacity) {
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
			frameRate = memorySum / memoryLen;
		}
		
		public final void reset() {
			frameRate = 0.0;
			memoryIdxR = 0;
			memoryIdxW = 0;
			memoryLen = 0;
			memorySum = 0.0;
			Arrays.fill(memory, 0.0);
		}
		
		public final double get() {
			return frameRate;
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