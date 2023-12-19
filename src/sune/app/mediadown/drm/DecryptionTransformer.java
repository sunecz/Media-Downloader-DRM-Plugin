package sune.app.mediadown.drm;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import sune.app.mediadown.conversion.ConversionMedia;
import sune.app.mediadown.drm.event.DecryptionEvent;
import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventBindable;
import sune.app.mediadown.event.EventRegistry;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.tracker.Trackable;
import sune.app.mediadown.event.tracker.TrackerEvent;
import sune.app.mediadown.gui.table.ResolvedMedia;
import sune.app.mediadown.media.Media;
import sune.app.mediadown.media.MediaUtils;
import sune.app.mediadown.pipeline.AbstractPipelineTask;
import sune.app.mediadown.pipeline.ConversionPipelineTask;
import sune.app.mediadown.pipeline.DownloadPipelineResult;
import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineResult;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadown.pipeline.PipelineTransformer;
import sune.app.mediadown.pipeline.TerminatingPipelineTask;
import sune.app.mediadown.plugin.PluginBase;
import sune.app.mediadown.plugin.PluginConfiguration;
import sune.app.mediadown.plugin.PluginLoaderContext;
import sune.app.mediadown.transformer.Transformer;
import sune.app.mediadown.util.Utils.Ignore;

public final class DecryptionTransformer implements Transformer {
	
	private static final PluginBase PLUGIN = PluginLoaderContext.getContext().getInstance();
	
	DecryptionTransformer() {
	}
	
	private static final PluginConfiguration configuration() {
		return PLUGIN.getContext().getConfiguration();
	}
	
	@Override
	public boolean isUsable(ResolvedMedia media) {
		return media.media().metadata().isProtected() && MediaUtils.isSegmentedMedia(media.media());
	}
	
	@Override
	public PipelineTransformer pipelineTransformer() {
		return new DecryptionPipelineTransformer();
	}
	
	private static final class DecryptionPipelineTransformer implements PipelineTransformer {
		
		@Override
		public PipelineResult transform(PipelineResult result) {
			if(result instanceof DownloadPipelineResult) {
				// Replace only the result of a download that requires decryption
				return new DecryptionNeededPipelineResult((DownloadPipelineResult) result);
			}
			
			return result;
		}
		
		@Override
		public PipelineTask transform(PipelineTask task) {
			return task;
		}
	}
	
	private static final class DecryptionPipelineTask extends AbstractPipelineTask {
		
		private final DownloadPipelineResult originalResult;
		
		private Decryptor decryptor;
		
		public DecryptionPipelineTask(DownloadPipelineResult originalResult) {
			this.originalResult = originalResult;
		}
		
		private static final void bindAllDecryptionEvents(
				EventBindable<DecryptionEvent> bindable,
				EventRegistry<EventType> eventRegistry
		) {
			for(Event<DecryptionEvent, ?> event : DecryptionEvent.values()) {
				bindable.addEventListener(
					event,
					(o) -> eventRegistry.call(TrackerEvent.UPDATE, ((Trackable) o).trackerManager().tracker())
				);
			}
		}
		
		private final List<Media> inputMedia() {
			return originalResult.inputs().stream().map(ConversionMedia::media).collect(Collectors.toList());
		}
		
		private final List<Path> inputPaths() {
			return originalResult.inputs().stream().map(ConversionMedia::path).collect(Collectors.toList());
		}
		
		private final int keysMaxRetryAttempts() {
			return configuration().intValue("keysMaxRetryAttempts");
		}
		
		private final int waitOnRetryMs() {
			return configuration().intValue("waitOnRetryMs");
		}
		
		@Override
		public PipelineResult doRun(Pipeline pipeline) throws Exception {
			decryptor = new Decryptor(
				originalResult.output().media(), inputMedia(), inputPaths(), keysMaxRetryAttempts(), waitOnRetryMs()
			);
			bindAllDecryptionEvents(decryptor, pipeline.getEventRegistry()); // Bind all events from the pipeline
			Ignore.Cancellation.callVoid(decryptor::start); // Wait for the decryption to finish
			return new DecryptionDonePipelineResult(originalResult);
		}
	}
	
	private static final class DecryptionNeededPipelineResult implements PipelineResult {
		
		private final DownloadPipelineResult originalResult;
		
		public DecryptionNeededPipelineResult(DownloadPipelineResult originalResult) {
			this.originalResult = originalResult;
		}
		
		@Override
		public DecryptionPipelineTask process(Pipeline pipeline) throws Exception {
			return new DecryptionPipelineTask(originalResult);
		}
		
		@Override
		public boolean isTerminating() {
			return false;
		}
	}
	
	private static final class DecryptionDonePipelineResult implements PipelineResult {
		
		private final DownloadPipelineResult originalResult;
		
		public DecryptionDonePipelineResult(DownloadPipelineResult originalResult) {
			this.originalResult = originalResult;
		}
		
		@Override
		public PipelineTask process(Pipeline pipeline) throws Exception {
			if(originalResult.needConversion()) {
				return ConversionPipelineTask.of(
					originalResult.output(),
					originalResult.inputs(),
					originalResult.metadata()
				);
			}
			
			return TerminatingPipelineTask.getTypedInstance();
		}
		
		@Override
		public boolean isTerminating() {
			return originalResult.isTerminating();
		}
	}
}