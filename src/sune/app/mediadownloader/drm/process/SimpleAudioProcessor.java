package sune.app.mediadownloader.drm.process;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import sune.app.mediadownloader.drm.DRMLog;
import sune.app.mediadownloader.drm.util.Cut;
import sune.app.mediadownloader.drm.util.DRMUtils;
import sune.app.mediadownloader.drm.util.FFMpegTrimCommandGenerator;
import sune.app.mediadownloader.drm.util.FFMpegTrimCommandGenerator.TrimCommand;
import sune.app.mediadownloader.drm.util.RecordInfo;

public final class SimpleAudioProcessor implements AudioProcessor {
	
	private static final Logger logger = DRMLog.get();
	
	private final RecordInfo recordInfo;
	private final Path inputPath;
	private final double duration;
	
	private TrimCommand command;
	
	public SimpleAudioProcessor(RecordInfo recordInfo, Path inputPath, double duration) {
		this.recordInfo = recordInfo;
		this.inputPath = inputPath;
		this.duration = duration;
	}
	
	@Override
	public void process() throws Exception {
		if(logger.isDebugEnabled()) {
			logger.debug("Process audio: {}", inputPath.toString());
		}
		
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
		
		double includeLength = cutsInclude.stream().mapToDouble(Cut::length).sum();
		if(logger.isDebugEnabled()) {
			logger.debug("Include length: {}", includeLength);
		}
		
		command = FFMpegTrimCommandGenerator.forAudio(recordInfo.sampleRate()).command(cutsInclude);
	}
	
	public TrimCommand command() {
		return command;
	}
}