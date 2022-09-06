package sune.app.mediadownloader.drm.process;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;

import sune.api.process.ReadOnlyProcess;
import sune.app.mediadown.event.tracker.TrackerManager;
import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMLog;
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
	
	private final TrackerManager trackerManager;
	private final ProcessManager processManager;
	private final FilesManager filesManager;
	private final RecordInfo recordInfo;
	private final Path inputPath;
	private final Path outputPath;
	
	private double duration;
	
	public SimpleAudioProcessor(TrackerManager trackerManager, ProcessManager processManager,
			FilesManager filesManager, RecordInfo recordInfo, Path inputPath, Path outputPath) {
		this.trackerManager = trackerManager;
		this.processManager = processManager;
		this.filesManager = filesManager;
		this.recordInfo = recordInfo;
		this.inputPath = inputPath;
		this.outputPath = outputPath;
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
		
		List<Cut.OfDouble> cuts = new ArrayList<>(recordInfo.audioCuts());
		Cut.OfDouble cutOff = recordInfo.cutOff();
		
		if(cutOff.start() > 0.0) {
			cuts.add(0, new Cut.OfDouble(0.0, cutOff.start()));
		}
		
		if(cutOff.end() > 0.0 && cutOff.end() < duration) {
			cuts.add(new Cut.OfDouble(cutOff.end(), duration));
		}
		
		if(logger.isDebugEnabled()) {
			logger.debug("Audio cuts:");
			cuts.forEach((c) -> logger.debug(c.toString()));
		}
		
		// Filter the cuts so that there only valid ones
		List<Cut.OfDouble> cutsInclude = new ArrayList<>();
		double start = 0.0;
		for(Cut.OfDouble cut : cuts) {
			double end = cut.start();
			if(!DRMUtils.eq(start, end)) {
				cutsInclude.add(new Cut.OfDouble(start, end));
			}
			start = cut.end();
		}
		cutsInclude.add(new Cut.OfDouble(start, duration));
		
		double includeLength = cutsInclude.stream().map(Cut::length).reduce(0.0, (a, b) -> a + b).doubleValue();
		if(logger.isDebugEnabled())
			logger.debug("Include length: {}", includeLength);
		
		if(processManager.isStopped()) return; // Stopped, do not continue
		
		PostProcessTracker.Factory<PostProcessOperation> processTrackerFactory
			= new PostProcessTracker.Factory<>(PostProcessOperation.class);
		PostProcessTracker tracker = processTrackerFactory.create(duration, PostProcessOperation.TRIM_AUDIO);
		trackerManager.setTracker(tracker);
		trackerManager.update();
		
		String args = "-c:a pcm_s16le";
		int sampleRate = recordInfo.sampleRate();
		Consumer<String> parser = new FFMpegTimeProgressParser(tracker);
		
		for(String command : FFMpegTrimCommandGenerator
				.forAudio(path, outputPath, sampleRate, args)
				.commands(cutsInclude)) {
			try(ReadOnlyProcess process = processManager.ffmpeg(parser)) {
				if(logger.isDebugEnabled())
					logger.debug("ffmpeg{}", command);
				process.execute(command);
				DRMProcessUtils.throwIfFailedFFMpeg(process.waitFor());
			}
		}
		
		filesManager.deleteNow(path);
	}
	
	public double duration() {
		return duration;
	}
	
	private static enum PostProcessOperation {
		
		FIX_AUDIO, TRIM_AUDIO;
	}
}