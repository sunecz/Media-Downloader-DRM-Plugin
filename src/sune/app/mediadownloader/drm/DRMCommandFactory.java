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
	
	public String recordCommand(String audioDeviceName, double frameRate, int sampleRate, String windowTitle,
			double audioOffset, Path recordPath) {
		StringBuilder builder = new StringBuilder();
		builder.append(" -y"); // Rewrite the output file, if it exists
		
		builder.append(" -f dshow"); // Record audio
		builder.append(" -thread_queue_size 1024 -probesize 8M -sample_rate %{sample_rate}d -channel_layout stereo"); // Input audio settings
		builder.append(" -copyts -start_at_zero"); // Help the synchronization
		builder.append(" -audio_buffer_size %{audio_buffer_size}d"); // Set audio buffer size for latency
		builder.append(" -i audio=\"%{audio_device_name}s\""); // Record specific audio input
		
		builder.append(" -f gdigrab"); // Record video
		builder.append(" -thread_queue_size 1024 -probesize 64M -fflags +igndts -framerate %{frame_rate}s -draw_mouse 0"); // Input video settings
		builder.append(" -copyts -start_at_zero"); // Help the synchronization
		builder.append(" -i title=\"%{window_title}s\""); // Record specific window
		
		switch(configuration.quality()) {
			case LOSSLESS:
				builder.append(" -c:v libx264rgb -r %{frame_rate}s"); // Output video settings
				builder.append(" -c:a pcm_s16le -ac 2 -ar %{sample_rate}d -channel_layout stereo"); // Output audio settings
				builder.append(" -preset ultrafast -tune zerolatency -qp 0 -pix_fmt rgb24 -g 1"); // Performance setting
				break;
			case HIGH:
				builder.append(" -c:v libx264rgb -r %{frame_rate}s"); // Output video settings
				builder.append(" -c:a pcm_s16le -ac 2 -ar %{sample_rate}d -channel_layout stereo"); // Output audio settings
				builder.append(" -preset ultrafast -tune zerolatency -crf 0 -pix_fmt rgb24"); // Performance setting
				break;
			case MEDIUM:
				builder.append(" -c:v libx264 -r %{frame_rate}s"); // Output video settings
				builder.append(" -c:a aac -ac 2 -ar %{sample_rate}d -channel_layout stereo"); // Output audio settings
				builder.append(" -preset ultrafast -tune zerolatency -crf 17 -pix_fmt yuv420p"); // Performance setting
				break;
			case LOW:
				builder.append(" -c:v libx264 -r %{frame_rate}s"); // Output video settings
				builder.append(" -c:a aac -ac 2 -ar %{sample_rate}d -channel_layout stereo"); // Output audio settings
				builder.append(" -preset ultrafast -tune zerolatency -crf 23 -pix_fmt yuv420p"); // Performance setting
				break;
			default:
				throw new IllegalStateException("Invalid quality");
		}
		
		builder.append(" -vf setpts=N/FR/TB"); // Ensure correct timestamps on pause/resume
		
		StringBuilder af = new StringBuilder();
		af.append(" asetpts=N/SR/TB"); // Ensure correct timestamps on pause/resume
		af.append(",asetnsamples=%{nsamples}d"); // Set information "precision"
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
			"audio_offset", DRMUtils.toString(audioOffset),
			"audio_buffer_size", DRMConstants.AUDIO_BUFFER_SIZE_MS,
			"window_title", windowTitle,
			"frame_rate", DRMUtils.toString(frameRate),
			"output", recordPath.toAbsolutePath().toString(),
			"progress_interval", DRMUtils.toString(1.0 / frameRate));
		
		return command;
	}
	
	public String videoProcessorCommandArguments() {
		switch(configuration.quality()) {
			case LOSSLESS:
				return "-c:v libx264rgb -preset ultrafast -tune film -qp 0 -pix_fmt rgb24 -g 1";
			case HIGH:
				return "-c:v libx264rgb -preset ultrafast -tune film -crf 0 -pix_fmt rgb24";
			case MEDIUM:
				return "-c:v libx264 -preset ultrafast -tune film -crf 17 -pix_fmt yuv420p";
			case LOW:
				return "-c:v libx264 -preset ultrafast -tune film -crf 23 -pix_fmt yuv420p";
			default:
				throw new IllegalStateException("Invalid quality");
		}
	}
	
	public String audioProcessorCommandArguments() {
		switch(configuration.quality()) {
			case LOSSLESS:
				return "-c:a pcm_s16le";
			case HIGH:
				return "-c:a pcm_s16le";
			case MEDIUM:
				return "-c:a aac";
			case LOW:
				return "-c:a aac";
			default:
				throw new IllegalStateException("Invalid quality");
		}
	}
	
	public String videoProcessorCommandFileExtension() {
		return "mkv";
	}
	
	public String audioProcessorCommandFileExtension() {
		switch(configuration.quality()) {
			case LOSSLESS:
				return "wav";
			case HIGH:
				return "wav";
			case MEDIUM:
				return "aac";
			case LOW:
				return "aac";
			default:
				throw new IllegalStateException("Invalid quality");
		}
	}
}