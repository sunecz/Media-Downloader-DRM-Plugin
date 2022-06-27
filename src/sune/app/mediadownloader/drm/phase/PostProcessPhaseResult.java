package sune.app.mediadownloader.drm.phase;

import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineResult;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadown.pipeline.TerminatingPipelineTask;
import sune.app.mediadownloader.drm.DRMContext;

public class PostProcessPhaseResult implements PipelineResult<PostProcessPhaseResult> {
	
	private final DRMContext context;
	
	public PostProcessPhaseResult(DRMContext context) {
		this.context = context;
	}
	
	@Override
	public PipelineTask<PostProcessPhaseResult> process(Pipeline pipeline) throws Exception {
		return TerminatingPipelineTask.getTypedInstance();
	}
	
	@Override
	public boolean isTerminating() {
		return true;
	}
	
	public DRMContext context() {
		return context;
	}
}