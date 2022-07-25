package sune.app.mediadownloader.drm;

public final class DRMConstants {
	
	// ----- General
	public static final int MS_IN_SEC = 1000;
	public static final int PORT_MIN  = 1;
	public static final int PORT_MAX  = 65535;
	public static final int TIMEOUT   = 8000;
	
	// ----- Proxy
	public static final String PROXY_DOMAIN = "127.0.0.1";
	public static final int    PROXY_PORT   = 9876;
	
	// ----- Record
	public static final double DEFAULT_SILENCE_THRESHOLD = -90.0;
	public static final double DEFAULT_FRAMERATE         = 24.0;
	public static final int    AUDIO_LISTEN_SERVER_PORT  = 9877;
	public static final int    AUDIO_BUFFER_SIZE_MS      = 100;
	// Default VB-Audio Virtual Cable settings
	public static final int    AUDIO_MAX_LATENCY_SAMPLES = 7168;
	public static final int    AUDIO_OUTPUT_SAMPLE_RATE  = 44100;
	
	// ----- Other
	public static final String FRAME_TITLE_FORMAT = "Media Downloader - DRM Plugin [%s]";
	
	// Forbid anyone to create an instance of this class
	private DRMConstants() {
	}
}