package sune.app.mediadownloader.drm.process;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMLog;
import sune.app.mediadownloader.drm.tracker.EnumNameTracker;
import sune.app.mediadownloader.drm.tracker.PostProcessTracker;
import sune.app.mediadownloader.drm.util.Cut;
import sune.app.mediadownloader.drm.util.DRMProcessUtils;
import sune.app.mediadownloader.drm.util.DRMUtils;
import sune.app.mediadownloader.drm.util.FFMpegTimeProgressParser;
import sune.app.mediadownloader.drm.util.FFMpegTrimCommandGenerator;
import sune.app.mediadownloader.drm.util.FilesManager;
import sune.app.mediadownloader.drm.util.ProcessManager;
import sune.app.mediadownloader.drm.util.RecordInfo;

public final class SimpleAudioProcessor implements AudioProcessor {
	
	private static final Logger logger = DRMLog.get();
	
	private static final double DEFAULT_NOISE_VOLUME = -80.0;
	private static final double SILENCE_RANGE_DURATION = 0.0001;
	private static final double SILENCE_RANGE_TOLERANCE = 1;
	
	private final TrackerManager trackerManager;
	private final ProcessManager processManager;
	private final FilesManager filesManager;
	private final RecordInfo recordInfo;
	private final Path inputPath;
	private final Path outputPath;
	
	private double duration;
	
	public SimpleAudioProcessor(TrackerManager trackerManager, ProcessManager processManager, FilesManager filesManager,
			RecordInfo recordInfo, Path inputPath, Path outputPath) {
		this.trackerManager = trackerManager;
		this.processManager = processManager;
		this.filesManager = filesManager;
		this.recordInfo = recordInfo;
		this.inputPath = inputPath;
		this.outputPath = outputPath;
	}
	
	private static final boolean intersects(Cut.OfDouble range, double start, double end) {
		return (range.start() >= start && range.start() <= end) || (range.end() >= start && range.end() <= end);
	}
	
	private final List<SimpleAudioProcessor.VolumeEntry> getVolumeEntries(Path path) throws Exception {
		SimpleAudioProcessor.VolumeLinesParser parser = new VolumeLinesParser();
		try(ReadOnlyProcess process = processManager.ffmpeg(parser)) {
			StringBuilder builder = new StringBuilder();
			builder.append(" -y -hide_banner -loglevel level+info -nostats");
			builder.append(" -i \"%{input}s\"");
			builder.append(" -af asetnsamples=1000,astats=metadata=1:reset=1,ametadata=print:key=lavfi.astats.Overall.RMS_level");
			builder.append(" -f null -");
			String command = Utils.format(builder.toString(),
				"input", path.toAbsolutePath().toString());
			if(logger.isDebugEnabled())
				logger.debug("ffmpeg{}", command);
			process.execute(command);
			DRMProcessUtils.throwIfFailedFFMpeg(process.waitFor());
		}
		return parser.entries();
	}
	
	private final List<Cut.OfDouble> silenceRanges(Path file, double start, double end, double frameRate, double dBNoise) throws Exception {
		SimpleAudioProcessor.SilenceLinesParser parser = new SilenceLinesParser();
		try(ReadOnlyProcess process = processManager.ffmpeg(parser)) {
			StringBuilder builder = new StringBuilder();
			builder.append(" -y -hide_banner -loglevel level+info -nostats"); // Minimize output and 'yes' all
			builder.append(" -i \"%{input}s\""); // Specify input file
			builder.append(" -af \"aselect='between(t,%{start}s,%{end}s)',silencedetect=n=%{noise}sdB:d=%{duration}s\""); // Detect silence
			builder.append(" -f null -"); // Output only to console
			String command = Utils.format(builder.toString(),
				"input", file.toAbsolutePath().toString(),
				"start", DRMUtils.format("%.6f", start),
				"end", DRMUtils.format("%.6f", end),
				"noise", DRMUtils.format("%.4f", dBNoise),
				"duration", DRMUtils.format("%.4f", SILENCE_RANGE_DURATION));
			if(logger.isDebugEnabled())
				logger.debug("ffmpeg{}", command);
			process.execute(command);
			DRMProcessUtils.throwIfFailedFFMpeg(process.waitFor());
		}
		List<Cut.OfDouble> parsedRanges = parser.silences();
		if(parsedRanges.isEmpty()) return parsedRanges; // Fast-path
		List<Cut.OfDouble> ranges = new ArrayList<>();
		// Since we allow very small duration of silence, we have to manually merge them together
		double maxDiff = SILENCE_RANGE_DURATION + (1.0 / frameRate) * SILENCE_RANGE_TOLERANCE;
		Iterator<Cut.OfDouble> it = parsedRanges.iterator();
		Cut.OfDouble range = it.next();
		double silenceStart = range.start(), silenceEnd = range.end();
		while(it.hasNext()) {
			range = it.next();
			if(range.start() - silenceEnd <= maxDiff) {
				silenceEnd = range.end();
			} else {
				if(ranges.isEmpty() && silenceStart < maxDiff)
					silenceStart = 0.0; // Fix silence at the beginning
				ranges.add(new Cut.OfDouble(silenceStart, silenceEnd));
				silenceStart = range.start();
				silenceEnd = range.end();
			}
		}
		if(ranges.isEmpty() && silenceStart < maxDiff)
			silenceStart = 0.0; // Fix silence at the beginning
		ranges.add(new Cut.OfDouble(silenceStart, silenceEnd));
		return ranges;
	}
	
	private final double meanVolume(Path file) throws Exception {
		FFMpegVolumeDetectLineParser parser = new FFMpegVolumeDetectLineParser();
		try(ReadOnlyProcess process = processManager.ffmpeg(parser)) {
			StringBuilder builder = new StringBuilder();
			builder.append(" -y -hide_banner -loglevel level+info -nostats");
			//builder.append(" -ss %{time_start}s");
			builder.append(" -i \"%{input}s\"");
			//builder.append(" -to %{time_end}s");
			builder.append(" -af volumedetect");
			builder.append(" -f null -");
			String command = Utils.format(builder.toString(),
				"input", file.toAbsolutePath().toString());
			if(logger.isDebugEnabled())
				logger.debug("ffmpeg{}", command);
			process.execute(command);
			DRMProcessUtils.throwIfFailedFFMpeg(process.waitFor());
		}
		return parser.isValid() ? parser.mean() : DEFAULT_NOISE_VOLUME;
	}
	
	private final void extractFixedAudio(Path inputPath, Path outputPath) throws Exception {
		PostProcessTracker.Factory<PostProcessOperation> processTrackerFactory
			= new PostProcessTracker.Factory<>(PostProcessOperation.class);
		PostProcessTracker tracker = processTrackerFactory.create(-1.0, PostProcessOperation.FIX_AUDIO);
		trackerManager.setTracker(tracker);
		trackerManager.update();
		Consumer<String> parser = new FFMpegTimeProgressParser(tracker);
		try(ReadOnlyProcess process = processManager.ffmpeg(parser)) {
			StringBuilder builder = new StringBuilder();
			builder.append(" -y -hide_banner -v info");
			builder.append(" -i \"%{input}s\"");
			builder.append(" -c:a copy -vn");
			builder.append(" \"%{output}s\"");
			String command = Utils.format(builder.toString(),
				"input", inputPath.toAbsolutePath().toString(),
				"output", outputPath.toAbsolutePath().toString());
			if(logger.isDebugEnabled())
				logger.debug("ffmpeg{}", command);
			process.execute(command);
			DRMProcessUtils.throwIfFailedFFMpeg(process.waitFor());
		}
	}
	
	@Override
	public void process() throws Exception {
		if(logger.isDebugEnabled())
			logger.debug("Process audio: {}", inputPath.toString());
		
		Path path = inputPath.resolveSibling("audio.fix.wav");
		extractFixedAudio(inputPath, path);
		if(processManager.isStopped()) return; // Stopped, do not continue
		
		duration = DRMProcessUtils.duration(path);
		if(logger.isDebugEnabled())
			logger.debug("Duration: {}s", DRMUtils.format("%.6f", duration));
		
		EnumNameTracker.Factory<AudioProcessorPhases> trackerFactory
			= new EnumNameTracker.Factory<>(AudioProcessorPhases.class);
		EnumNameTracker enumTracker;
		
		enumTracker = trackerFactory.create(AudioProcessorPhases.MEAN_VOLUME);
		trackerManager.setTracker(enumTracker);
		trackerManager.update();
		double meanVolume = meanVolume(path);
		if(logger.isDebugEnabled())
			logger.debug("Mean volume: {} dB", DRMUtils.format("%.6f", meanVolume));
		if(processManager.isStopped()) return; // Stopped, do not continue
		
		enumTracker = trackerFactory.create(AudioProcessorPhases.VOLUME_ENTRIES);
		trackerManager.setTracker(enumTracker);
		trackerManager.update();
		SimpleAudioProcessor.Volumes volumes = new Volumes(getVolumeEntries(path));
		double meanSilence = volumes.min(0.0, duration);
		double noiseBase = volumes.noiseBaseVolume(meanVolume, meanSilence);
		if(logger.isDebugEnabled())
			logger.debug("Noise base: {} dB", DRMUtils.format("%.6f", noiseBase));
		if(processManager.isStopped()) return; // Stopped, do not continue
		
		enumTracker = trackerFactory.create(AudioProcessorPhases.SILENCE_RANGES);
		trackerManager.setTracker(enumTracker);
		trackerManager.update();
		List<Cut.OfDouble> silences = silenceRanges(path, 0.0, duration, recordInfo.frameRate(), noiseBase);
		if(logger.isDebugEnabled()) {
			logger.debug("Silences:");
			silences.forEach((c) -> logger.debug(c.toString()));
		}
		if(processManager.isStopped()) return; // Stopped, do not continue
		
		List<Cut.OfDouble> cuts = new ArrayList<>(recordInfo.cuts());
		if(recordInfo.startCutOff() > 0.0) {
			cuts.add(0, new Cut.OfDouble(0.0, recordInfo.startCutOff()));
		}
		if(recordInfo.endCutOff() > 0.0 && recordInfo.endCutOff() < duration) {
			cuts.add(new Cut.OfDouble(recordInfo.endCutOff(), duration));
		}
		
		if(logger.isDebugEnabled()) {
			logger.debug("Cuts:");
			cuts.forEach((c) -> logger.debug(c.toString()));
		}
		
		enumTracker = trackerFactory.create(AudioProcessorPhases.CUTS_GENERATE);
		trackerManager.setTracker(enumTracker);
		trackerManager.update();
		List<Cut.OfDouble> audioCuts = new ArrayList<>();
		if(!silences.isEmpty()) {
			if(logger.isDebugEnabled())
				logger.debug("Mode: Silence-based");
			
			Iterator<Cut.OfDouble> it = silences.iterator();
			Cut.OfDouble silence = it.next();
			for(Cut.OfDouble cut : cuts) {
				double start = cut.start(), end = cut.end();
				
				if(logger.isDebugEnabled())
					logger.debug("Cut: {} -> {}", DRMUtils.format("%.6f", start), DRMUtils.format("%.6f", end));
				
				// Check if the cut happened in a silence
				if(silence.start() < start && silence.end() > end) {
					if(logger.isDebugEnabled())
						logger.debug("Result: Keep (reason: in silence)");
					audioCuts.add(cut); // Just keep the cut
					continue; // Continue looping cuts
				}
				
				// Find the next first silence that intersects the current cut
				while(!intersects(silence, start, end) && silence.start() < end) {
					if(!it.hasNext()) break;
					silence = it.next();
				}
				
				if(silence.start() >= end || !it.hasNext()) {
					if(logger.isDebugEnabled())
						logger.debug("Result: Keep (reason: no silence)");
					audioCuts.add(cut); // Just keep the cut
					continue;
				}
				
				double silenceStart = start, silenceEnd = end, silenceLength = 0.0;
				
				do {
					if(logger.isDebugEnabled())
						logger.debug("Silence: {} -> {}", DRMUtils.format("%.6f", silence.start()), DRMUtils.format("%.6f", silence.end()));
					silenceStart = silence.start();
					silenceEnd = Math.min(silence.end(), end);
					
					if(logger.isDebugEnabled())
						logger.debug("New cut: {} -> {}", DRMUtils.format("%.6f", silenceStart), DRMUtils.format("%.6f", silenceEnd));
					audioCuts.add(new Cut.OfDouble(silenceStart, silenceEnd));
					silenceLength += silenceEnd - silenceStart;
					
					// Set variables if the loop exits now
					silenceStart = end;
					silenceEnd = silence.end();
					
					if(!it.hasNext()) break;
					silence = it.next();
				} while(silence.start() < end);
				
				boolean isStartCutOff = DRMUtils.eq(recordInfo.startCutOff(), end);
				
				double cutStart = silenceStart;
				double cutEnd = Math.min(end + (isStartCutOff ? 0.0 : silenceLength), silenceEnd);
				
				if(!DRMUtils.eq(cutStart, cutEnd)) {
					
					// Merge the lastly added cut and the one that should be added now, if possible
					if(!audioCuts.isEmpty()) {
						int lastIndex = audioCuts.size() - 1;
						Cut.OfDouble lastCut = audioCuts.get(lastIndex);
						if(DRMUtils.eq(cutStart, lastCut.end())) {
							if(logger.isDebugEnabled())
								logger.debug("Before merge: {} -> {}", DRMUtils.format("%.6f", cutStart), DRMUtils.format("%.6f", cutEnd));
							audioCuts.remove(lastIndex);
							cutStart = lastCut.start();
						}
					}
					if(logger.isDebugEnabled())
						logger.debug("New cut: {} -> {}", DRMUtils.format("%.6f", cutStart), DRMUtils.format("%.6f", cutEnd));
					audioCuts.add(new Cut.OfDouble(cutStart, cutEnd));
				}
			}
		} else {
			if(logger.isDebugEnabled())
				logger.debug("Mode: Copy-based");
			audioCuts.addAll(cuts);
		}
		
		if(logger.isDebugEnabled()) {
			logger.debug("Final cuts:");
			audioCuts.forEach((c) -> logger.debug(c.toString()));
		}
		
		// Filter the cuts so that there only valid ones
		List<Cut.OfDouble> cutsInclude = new ArrayList<>();
		double start = 0.0;
		for(Cut.OfDouble cut : audioCuts) {
			double end = cut.start();
			if(!DRMUtils.eq(start, end)) {
				cutsInclude.add(new Cut.OfDouble(start, end));
			}
			start = cut.end();
		}
		cutsInclude.add(new Cut.OfDouble(start, duration));
		
		double includeLength = cutsInclude.stream().map(Cut.OfDouble::length).reduce(0.0, (a, b) -> a + b).doubleValue();
		if(logger.isDebugEnabled())
			logger.debug("Include length: {}", includeLength);
		
		if(processManager.isStopped()) return; // Stopped, do not continue
		
		int sampleRate = recordInfo.sampleRate();
		PostProcessTracker.Factory<PostProcessOperation> processTrackerFactory
			= new PostProcessTracker.Factory<>(PostProcessOperation.class);
		PostProcessTracker tracker = processTrackerFactory.create(duration, PostProcessOperation.TRIM_AUDIO);
		trackerManager.setTracker(tracker);
		trackerManager.update();
		Consumer<String> parser = new FFMpegTimeProgressParser(tracker);
		List<String> commands = FFMpegTrimCommandGenerator.forAudio(path, outputPath, sampleRate).commands(cutsInclude);
		for(String command : commands) {
			try(ReadOnlyProcess process = processManager.ffmpeg(parser)) {
				if(logger.isDebugEnabled())
					logger.debug("ffmpeg{}", command);
				process.execute(command);
				DRMProcessUtils.throwIfFailedFFMpeg(process.waitFor());
			}
		}
		
		filesManager.delete(path);
	}
	
	public double duration() {
		return duration;
	}
	
	private static enum AudioProcessorPhases {
		
		MEAN_VOLUME, VOLUME_ENTRIES, SILENCE_RANGES, CUTS_GENERATE;
	}
	
	private static enum PostProcessOperation {
		
		FIX_AUDIO, TRIM_AUDIO;
	}
	
	private static final class VolumeEntry {
		
		private final double time;
		private final double volume;
		
		public VolumeEntry(double time, double volume) {
			this.time = time;
			this.volume = volume;
		}
		
		public double time() { return time; }
		public double volume() { return volume; }
	}
	
	private static final class VolumeLinesParser implements Consumer<String> {
		
		private final List<SimpleAudioProcessor.VolumeEntry> entries = new ArrayList<>();
		private double time = Double.NaN;
		
		private static final Pattern REGEX_TIME = Pattern.compile("^.*?pts_time:([-+]?\\d+(?:\\.\\d+)?)$");
		private static final Pattern REGEX_VOLUME = Pattern.compile("^.*?RMS_level=([-+]?\\d+(?:\\.\\d+)?)$");
		
		@Override
		public void accept(String line) {
			Matcher matcher;
			if((matcher = REGEX_TIME.matcher(line)).matches()) {
				time = Double.parseDouble(matcher.group(1));
			} else if(!Double.isNaN(time) && (matcher = REGEX_VOLUME.matcher(line)).matches()) {
				double volume = Double.parseDouble(matcher.group(1));
				entries.add(new VolumeEntry(time, volume));
				time = Double.NaN;
			}
		}
		
		public List<SimpleAudioProcessor.VolumeEntry> entries() { return entries; }
	}
	
	// Non-optimized class for extracting sublists for specified time range
	private static final class Volumes {
		
		private static final class _Pair<A, B> {
			
			A _a; B _b;
			_Pair(A a, B b) { set(a, b); }
			final Volumes._Pair<A, B> set(A a, B b) { _a = a; _b = b; return this; }
			A a() { return _a; }
			B b() { return _b; }
		}
		
		private static final double LOG_BASE = 2.0;
		private static final double INV_LOG2 = 1.0 / Math.log(LOG_BASE);
		private static final double NOISE_BASE_FACTOR = 0.85; // 85% lerp
		private static final double NOISE_BASE_MIN = -90.0; // in dB
		
		private final List<SimpleAudioProcessor.VolumeEntry> entries;
		
		public Volumes(List<SimpleAudioProcessor.VolumeEntry> entries) {
			this.entries = entries;
		}
		
		public Stream<SimpleAudioProcessor.VolumeEntry> range(double start, double end) {
			return entries.stream().filter((e) -> e.time() >= start && e.time() <= end);
		}
		
		private static final <A> double rootSquareMean(Stream<A> stream, Function<A, Double> convertor) {
			Volumes._Pair<Double, Integer> pair = stream.map((v) -> new Volumes._Pair<>(convertor.apply(v), 1))
					.reduce(new Volumes._Pair<>(0.0, 0), (a, b) -> a.set(a.a() + b.a() * b.a(), a.b() + b.b()));
			return -Math.sqrt(pair.a() / pair.b());
		}
		
		private static final <A> double min(Stream<A> stream, Function<A, Double> convertor) {
			return stream.map(convertor).min(Double::compare).get();
		}
		
		@SuppressWarnings("unused")
		public double mean(double start, double end) {
			return rootSquareMean(range(start, end), VolumeEntry::volume);
		}
		
		public double min(double start, double end) {
			return min(range(start, end), VolumeEntry::volume);
		}
		
		public double noiseBaseVolume(double meanMin, double meanMax) {
			double min = Math.log(-meanMin) * INV_LOG2;
			double max = Math.log(-meanMax) * INV_LOG2;
			double val = -Math.pow(LOG_BASE, min + NOISE_BASE_FACTOR * (max - min));
			return Math.max(NOISE_BASE_MIN, val);
		}
	}
	
	private static final class SilenceLinesParser implements Consumer<String> {
		
		private static final Pattern PATTERN;
		
		static {
			PATTERN = Pattern.compile("^\\[silencedetect[^\\]]+\\]\\s+\\[info\\]\\s+silence_(start|end):\\s+([^\\|]+).*?$");
		}
		
		private final List<Cut.OfDouble> silences = new ArrayList<>();
		private double start = 0.0;
		
		@Override
		public void accept(String line) {
			Matcher matcher = PATTERN.matcher(line);
			if(!matcher.matches()) return; // Ignore non-silencedetect lines
			boolean isStart = matcher.group(1).equals("start");
			double value = Double.valueOf(matcher.group(2));
			if(isStart) start = value;
			else silences.add(new Cut.OfDouble(start, value));
		}
		
		public List<Cut.OfDouble> silences() { return silences; }
	}
	
	private static final class FFMpegVolumeDetectLineParser implements Consumer<String> {
		
		private static final int DEFAULT_HISTOGRAM_SIZE = 16;
		
		private static final Pattern PATTERN;
		private static final Pattern PATTERN_HISTOGRAM;
		private static final Pattern PATTERN_DBVAL;
		
		static {
			PATTERN = Pattern.compile("^\\[[^\\]]+volumedetect[^\\]]+\\]\\s+\\[info\\]\\s+([^:]+):\\s+(.*)$");
			PATTERN_HISTOGRAM = Pattern.compile("^histogram_(\\d+)db$");
			PATTERN_DBVAL = Pattern.compile("^([\\-\\+]?(?:\\d*\\.\\d+|\\d+))\\s+dB$");
		}
		
		private double mean = Double.NaN;
		private double max = Double.NaN;
		private int[] histogram = new int[DEFAULT_HISTOGRAM_SIZE];
		private int histogramMin = Integer.MAX_VALUE;
		private int histogramMax = Integer.MIN_VALUE;
		
		private final double dBValue(String value) {
			Matcher matcher = PATTERN_DBVAL.matcher(value);
			return matcher.matches() ? Double.parseDouble(matcher.group(1)) : Double.NaN;
		}
		
		@Override
		public void accept(String line) {
			Matcher matcher = PATTERN.matcher(line);
			if(!matcher.matches()) return; // Ignore non-volumedetect lines
			String name = matcher.group(1);
			String value = matcher.group(2);
			switch(name) {
				case "mean_volume": mean = dBValue(value); break;
				case "max_volume": max = dBValue(value); break;
				default:
					Matcher m = PATTERN_HISTOGRAM.matcher(name);
					if(m.matches()) {
						int dBLevel = Integer.parseInt(m.group(1));
						if(dBLevel < histogramMin) histogramMin = dBLevel;
						if(dBLevel > histogramMax) histogramMax = dBLevel;
						if(histogram.length <= dBLevel) {
							int newLength = histogram.length;
							while(newLength <= dBLevel) newLength <<= 1;
							int[] newHistogram = new int[newLength];
							System.arraycopy(histogram, 0, newHistogram, 0, histogram.length);
							histogram = newHistogram;
						}
						histogram[dBLevel] = Integer.parseInt(value);
					}
					break;
			}
		}
		
		public double mean() { return mean; }
		@SuppressWarnings("unused")
		public double max() { return max; }
		@SuppressWarnings("unused")
		public int[] histogram() { return Arrays.copyOf(histogram, histogramMax + 1); }
		
		public boolean isValid() {
			return !Double.isNaN(mean) && !Double.isNaN(max);
		}
	}
}