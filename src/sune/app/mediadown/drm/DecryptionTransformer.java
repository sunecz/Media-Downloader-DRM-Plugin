package sune.app.mediadown.drm;

import java.util.function.Function;

import sune.app.mediadown.drm.event.DecryptionEvent;
import sune.app.mediadown.drm.util.MediaDecryptionKey;
import sune.app.mediadown.event.Event;
import sune.app.mediadown.event.EventBindable;
import sune.app.mediadown.event.EventRegistry;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.tracker.Trackable;
import sune.app.mediadown.event.tracker.TrackerEvent;
import sune.app.mediadown.media.MediaUtils;
import sune.app.mediadown.media.ResolvedMedia;
import sune.app.mediadown.pipeline.AbstractPipelineTask;
import sune.app.mediadown.pipeline.DownloadPipelineResult;
import sune.app.mediadown.pipeline.MediaPipelineResult;
import sune.app.mediadown.pipeline.Pipeline;
import sune.app.mediadown.pipeline.PipelineMedia;
import sune.app.mediadown.pipeline.PipelineResult;
import sune.app.mediadown.pipeline.PipelineTask;
import sune.app.mediadown.pipeline.PipelineTransformer;
import sune.app.mediadown.pipeline.TerminatingPipelineResult;
import sune.app.mediadown.pipeline.TerminatingPipelineTask;
import sune.app.mediadown.plugin.PluginBase;
import sune.app.mediadown.plugin.PluginConfiguration;
import sune.app.mediadown.plugin.PluginLoaderContext;
import sune.app.mediadown.transformer.Transformer;
import sune.app.mediadown.util.CheckedConsumer;
import sune.app.mediadown.util.Metadata;
import sune.app.mediadown.util.Utils.Ignore;

public final class DecryptionTransformer implements Transformer {
	
	private static final PluginBase PLUGIN = PluginLoaderContext.getContext().getInstance();
	
	DecryptionTransformer() {
	}
	
	private static final PluginConfiguration configuration() {
		return PLUGIN.getContext().getConfiguration();
	}
	
	private static final <T extends EventType> void bindAllEvents(
			EventBindable<T> bindable,
			EventRegistry<EventType> eventRegistry,
			Event<T, ?>[] events
	) {
		for(Event<T, ?> event : events) {
			bindable.addEventListener(
				event,
				(o) -> eventRegistry.call(TrackerEvent.UPDATE, ((Trackable) o).trackerManager().tracker())
			);
		}
	}
	
	@Override
	public boolean isUsable(ResolvedMedia media) {
		// Replace only the result of a download that requires decryption
		return media.media().metadata().isProtected() && MediaUtils.isSegmentedMedia(media.media());
	}
	
	@Override
	public PipelineTransformer pipelineTransformer() {
		return new DecryptionPipelineTransformer();
	}
	
	private static final class DecryptionPipelineTransformer implements PipelineTransformer {
		
		@Override
		public PipelineResult transform(PipelineResult result) {
			if(result instanceof MediaPipelineResult) {
				// Obtain the decryption keys before downloading anything
				return new DecryptionKeyObtainNeededPipelineResult((MediaPipelineResult) result);
			}
			
			if(result instanceof WrappedPipelineResult) {
				WrappedPipelineResult wrapped = (WrappedPipelineResult) result;
				PipelineResult underlying = wrapped.originalResult;
				
				if(underlying instanceof DownloadPipelineResult) {
					// Decrypt the files after downloading them
					return new DecryptionNeededPipelineResult((DownloadPipelineResult) underlying, wrapped.metadata);
				}
			}
			
			return result;
		}
		
		@Override
		public PipelineTask transform(PipelineTask task) {
			return task;
		}
	}
	
	private static class WrappedPipelineResult implements PipelineResult {
		
		private final PipelineResult originalResult;
		private final Metadata metadata;
		
		public WrappedPipelineResult(PipelineResult originalResult, Metadata metadata) {
			this.originalResult = originalResult;
			this.metadata = metadata;
		}
		
		@Override
		public PipelineTask process(Pipeline pipeline) throws Exception {
			if(originalResult == null) {
				return TerminatingPipelineTask.getInstance();
			}
			
			return new WrappedPipelineTask(originalResult.process(pipeline), metadata);
		}
		
		@Override
		public boolean isTerminating() {
			return originalResult == null || originalResult.isTerminating();
		}
	}
	
	private static class WrappedPipelineTask implements PipelineTask {
		
		private final PipelineTask originalTask;
		private final Metadata metadata;
		
		public WrappedPipelineTask(PipelineTask originalTask, Metadata metadata) {
			this.originalTask = originalTask;
			this.metadata = metadata;
		}
		
		private final void doAction(CheckedConsumer<PipelineTask> action) throws Exception {
			if(originalTask == null) {
				return;
			}
			
			action.accept(originalTask);
		}
		
		private final <T> T doAction(Function<PipelineTask, T> action, T defaultValue) {
			return originalTask == null ? defaultValue : action.apply(originalTask);
		}
		
		@Override
		public PipelineResult run(Pipeline pipeline) throws Exception {
			if(originalTask == null) {
				return TerminatingPipelineResult.getInstance();
			}
			
			return new WrappedPipelineResult(originalTask.run(pipeline), metadata);
		}
		
		@Override public void stop() throws Exception { doAction(PipelineTask::stop); }
		@Override public void pause() throws Exception { doAction(PipelineTask::pause); }
		@Override public void resume() throws Exception { doAction(PipelineTask::resume); }
		
		@Override public boolean isRunning() { return doAction(PipelineTask::isRunning, false); }
		@Override public boolean isDone() { return doAction(PipelineTask::isDone, false); }
		@Override public boolean isStarted() { return doAction(PipelineTask::isStarted, false); }
		@Override public boolean isPaused() { return doAction(PipelineTask::isPaused, false); }
		@Override public boolean isStopped() { return doAction(PipelineTask::isStopped, false); }
		@Override public boolean isError() { return doAction(PipelineTask::isError, false); }
	}
	
	private static final class DecryptionKeyObtainPipelineTask extends AbstractPipelineTask {
		
		private final MediaPipelineResult originalResult;
		
		private DecryptionKeyObtainer obtainer;
		
		public DecryptionKeyObtainPipelineTask(MediaPipelineResult originalResult) {
			this.originalResult = originalResult;
		}
		
		private static final int keysMaxRetryAttempts() {
			return configuration().intValue("keysMaxRetryAttempts");
		}
		
		private static final int waitOnRetryMs() {
			return configuration().intValue("waitOnRetryMs");
		}
		
		@Override
		protected void doPause() throws Exception {
			if(obtainer != null) {
				obtainer.pause();
			}
		}
		
		@Override
		protected void doResume() throws Exception {
			if(obtainer != null) {
				obtainer.resume();
			}
		}
		
		@Override
		protected void doStop() throws Exception {
			if(obtainer != null) {
				obtainer.stop();
			}
		}
		
		@Override
		public PipelineResult doRun(Pipeline pipeline) throws Exception {
			PipelineMedia pipelineMedia = originalResult.media();
			obtainer = new DecryptionKeyObtainer(
				pipelineMedia.media(), pipelineMedia.destination(), keysMaxRetryAttempts(), waitOnRetryMs()
			);
			bindAllEvents(obtainer, pipeline.getEventRegistry(), DecryptionEvent.values());
			Ignore.Cancellation.callVoid(obtainer::start); // Wait for the decryption to finish
			MediaDecryptionKey keyVideo = obtainer.keyVideo();
			MediaDecryptionKey keyAudio = obtainer.keyAudio();
			return new DecryptionKeyObtainDonePipelineResult(originalResult, keyVideo, keyAudio);
		}
	}
	
	private static final class DecryptionPipelineTask extends AbstractPipelineTask {
		
		private final DownloadPipelineResult originalResult;
		private final MediaDecryptionKey keyVideo;
		private final MediaDecryptionKey keyAudio;
		
		private Decryptor decryptor;
		
		public DecryptionPipelineTask(
				DownloadPipelineResult originalResult, MediaDecryptionKey keyVideo, MediaDecryptionKey keyAudio
		) {
			this.originalResult = originalResult;
			this.keyVideo = keyVideo;
			this.keyAudio = keyAudio;
		}
		
		@Override
		protected void doPause() throws Exception {
			if(decryptor != null) {
				decryptor.pause();
			}
		}
		
		@Override
		protected void doResume() throws Exception {
			if(decryptor != null) {
				decryptor.resume();
			}
		}
		
		@Override
		protected void doStop() throws Exception {
			if(decryptor != null) {
				decryptor.stop();
			}
		}
		
		@Override
		public PipelineResult doRun(Pipeline pipeline) throws Exception {
			decryptor = new Decryptor(
				originalResult.inputs(), keyVideo, keyAudio
			);
			bindAllEvents(decryptor, pipeline.getEventRegistry(), DecryptionEvent.values());
			Ignore.Cancellation.callVoid(decryptor::start); // Wait for the decryption to finish
			return new DecryptionDonePipelineResult(originalResult);
		}
	}
	
	private static final class DecryptionKeyObtainNeededPipelineResult implements PipelineResult {
		
		private final MediaPipelineResult originalResult;
		
		public DecryptionKeyObtainNeededPipelineResult(MediaPipelineResult originalResult) {
			this.originalResult = originalResult;
		}
		
		@Override
		public DecryptionKeyObtainPipelineTask process(Pipeline pipeline) throws Exception {
			return new DecryptionKeyObtainPipelineTask(originalResult);
		}
		
		@Override public boolean isTerminating() { return false; }
	}
	
	private static final class DecryptionKeyObtainDonePipelineResult extends WrappedPipelineResult {
		
		public DecryptionKeyObtainDonePipelineResult(
				MediaPipelineResult originalResult, MediaDecryptionKey keyVideo, MediaDecryptionKey keyAudio
		) {
			super(originalResult, Metadata.of("key.video", keyVideo, "key.audio", keyAudio));
		}
	}
	
	private static final class DecryptionNeededPipelineResult implements PipelineResult {
		
		private final DownloadPipelineResult originalResult;
		private final Metadata metadata;
		
		public DecryptionNeededPipelineResult(DownloadPipelineResult originalResult, Metadata metadata) {
			this.originalResult = originalResult;
			this.metadata = metadata;
		}
		
		@Override
		public DecryptionPipelineTask process(Pipeline pipeline) throws Exception {
			MediaDecryptionKey keyVideo = metadata.get("key.video");
			MediaDecryptionKey keyAudio = metadata.get("key.audio");
			return new DecryptionPipelineTask(originalResult, keyVideo, keyAudio);
		}
		
		@Override public boolean isTerminating() { return false; }
	}
	
	private static final class DecryptionDonePipelineResult implements PipelineResult {
		
		private final DownloadPipelineResult originalResult;
		
		public DecryptionDonePipelineResult(DownloadPipelineResult originalResult) {
			this.originalResult = originalResult;
		}
		
		@Override
		public PipelineTask process(Pipeline pipeline) throws Exception {
			return originalResult.process(pipeline);
		}
		
		@Override public boolean isTerminating() { return originalResult.isTerminating(); }
	}
}