package sune.app.mediadownloader.drm;

import java.nio.file.Path;
import java.util.Objects;

import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.util.DRMUtils;

// The default command factory, currently not extensible
public final class DRMCommandFactory {
	
	private final DRMConfiguration configuration;
	
	public DRMCommandFactory(DRMConfiguration configuration) {
		this.configuration = Objects.requireNonNull(configuration);
	}
	
	public String recordCommand(String audioDeviceName, double recordFrameRate, double frameRate, int sampleRate,
			String windowTitle, Path recordPath) {
		StringBuilder builder = new StringBuilder();
		builder.append(" -y"); // Rewrite the output file, if it exists
		
		builder.append(" -f dshow"); // Record audio
		builder.append(" -thread_queue_size 1024 -probesize 16M -sample_rate %{sample_rate}d -channel_layout stereo"); // Input audio settings
		builder.append(" -audio_buffer_size %{audio_buffer_size}d"); // Set audio buffer size for latency
		builder.append(" -copyts -start_at_zero"); // Help the synchronization
		builder.append(" -i audio=\"%{audio_device_name}s\""); // Record specific audio input
		
		builder.append(" -f gdigrab"); // Record video
		builder.append(" -thread_queue_size 1024 -probesize 128M -framerate %{record_frame_rate}s -draw_mouse 0"); // Input video settings
		builder.append(" -copyts -start_at_zero"); // Help the synchronization
		builder.append(" -i title=\"%{window_title}s\""); // Record specific window
		
		configuration.quality().recordCommandArguments(builder);
		
		builder.append(" -r %{frame_rate}s"); // Output video settings
		builder.append(" -ar %{sample_rate}d -channel_layout stereo"); // Output audio settings
		builder.append(" -preset ultrafast -tune zerolatency"); // Performance setting
		builder.append(" -vf mpdecimate"); // Make the video playback smoother
		
		StringBuilder af = new StringBuilder();
		af.append(" asetnsamples=%{nsamples}d"); // Set information "precision"
		af.append(",astats=metadata=1:reset=1"); // Set information "frequency"
		af.append(",ametadata=print:key=lavfi.astats.Overall.RMS_level"); // Get the audio volume level
		af.append(":file=\\'tcp\\://127.0.0.1\\:%{audio_server_port}d\\'"); // Send data to a TCP server (to reduce stdout)
		af.append(":direct=1"); // Avoid buffering, so it is "real-time"
		builder.append(" -af").append(af.toString()); // Set the built audio filter
		
		builder.append(" -hide_banner -loglevel warning"); // Make it less verbose
		builder.append(" -stats -stats_period %{progress_interval}s"); // Show stats and change them faster
		builder.append(" \"%{output}s\""); // Specify output file
		
		String command = Utils.format(builder.toString(),
			"audio_device_name", audioDeviceName,
			"sample_rate", sampleRate,
			"nsamples", sampleRate / DRMConstants.MS_IN_SEC,
			"audio_server_port", DRMConstants.AUDIO_LISTEN_SERVER_PORT,
			"audio_buffer_size", DRMConstants.AUDIO_BUFFER_SIZE_MS,
			"window_title", windowTitle,
			"record_frame_rate", DRMUtils.toString(recordFrameRate),
			"frame_rate", DRMUtils.toString(frameRate),
			"output", recordPath.toAbsolutePath().toString(),
			"progress_interval", DRMUtils.toString(1.0 / frameRate));
		
		return command;
	}
	
	public String videoCommandArguments() {
		return configuration.quality().videoCommandArguments();
	}
	
	public String audioCommandArguments() {
		return configuration.quality().audioCommandArguments();
	}
}