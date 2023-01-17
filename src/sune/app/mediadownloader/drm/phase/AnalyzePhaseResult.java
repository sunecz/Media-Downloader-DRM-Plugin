package sune.app.mediadownloader.drm.phase;

import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineResult;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadownloader.drm.DRMContext;

public class AnalyzePhaseResult implements PipelineResult<RecordPhaseResult> {
	
	private final DRMContext context;
	private final double duration;
	private final double recordFrameRate;
	private final double outputFrameRate;
	private final int sampleRate;
	
	public AnalyzePhaseResult(DRMContext context, double duration, double recordFrameRate, double outputFrameRate,
			int sampleRate) {
		this.context = context;
		this.duration = duration;
		this.recordFrameRate = recordFrameRate;
		this.outputFrameRate = outputFrameRate;
		this.sampleRate = sampleRate;
	}
	
	@Override
	public PipelineTask<RecordPhaseResult> process(Pipeline pipeline) throws Exception {
		return new RecordPhase(context, duration, recordFrameRate, outputFrameRate, sampleRate);
	}
	
	@Override
	public boolean isTerminating() {
		return false;
	}
}