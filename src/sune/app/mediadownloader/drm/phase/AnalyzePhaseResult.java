package sune.app.mediadownloader.drm.phase;

import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineResult;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadownloader.drm.DRMContext;

public class AnalyzePhaseResult implements PipelineResult<RecordPhaseResult> {
	
	private final DRMContext context;
	private final double duration;
	private final double frameRate;
	private final int sampleRate;
	
	public AnalyzePhaseResult(DRMContext context, double duration, double frameRate, int sampleRate) {
		this.context = context;
		this.duration = duration;
		this.frameRate = frameRate;
		this.sampleRate = sampleRate;
	}
	
	@Override
	public PipelineTask<RecordPhaseResult> process(Pipeline pipeline) throws Exception {
		return new RecordPhase(context, duration, frameRate, sampleRate);
	}
	
	@Override
	public boolean isTerminating() {
		return false;
	}
}