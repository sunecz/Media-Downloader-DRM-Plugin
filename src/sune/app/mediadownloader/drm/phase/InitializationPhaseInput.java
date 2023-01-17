package sune.app.mediadownloader.drm.phase;

import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineResult;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadownloader.drm.DRMContext;

public class InitializationPhaseInput implements PipelineResult<AnalyzePhaseResult> {
	
	private final DRMContext context;
	private final double duration;
	
	public InitializationPhaseInput(DRMContext context, double duration) {
		this.context = context;
		this.duration = duration;
	}
	
	@Override
	public PipelineTask<AnalyzePhaseResult> process(Pipeline pipeline) throws Exception {
		return new AnalyzePhase(context, duration);
	}
	
	@Override
	public boolean isTerminating() {
		return false;
	}
}