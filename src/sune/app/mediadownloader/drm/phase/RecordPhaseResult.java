package sune.app.mediadownloader.drm.phase;

import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineResult;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadownloader.drm.DRMContext;
import sune.app.mediadownloader.drm.util.RecordInfo;

public class RecordPhaseResult implements PipelineResult<PostProcessPhaseResult> {
	
	private final DRMContext context;
	private final RecordInfo recordInfo;
	
	public RecordPhaseResult(DRMContext context, RecordInfo recordInfo) {
		this.context = context;
		this.recordInfo = recordInfo;
	}
	
	@Override
	public PipelineTask<PostProcessPhaseResult> process(Pipeline pipeline) throws Exception {
		return new PostProcessPhase(context, recordInfo);
	}
	
	@Override
	public boolean isTerminating() {
		return false;
	}
}