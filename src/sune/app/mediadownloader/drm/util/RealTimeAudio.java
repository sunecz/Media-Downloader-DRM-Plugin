package sune.app.mediadownloader.drm.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import org.slf4j.Logger;

import sune.app.mediadown.concurrent.Threads;
import sune.app.mediadown.util.Regex;
import sune.app.mediadownloader.drm.DRMConstants;
import sune.app.mediadownloader.drm.DRMLog;

public final class RealTimeAudio {
	
	private static final Logger logger = DRMLog.get();
	
	private static final Regex REGEX_TIME   = Regex.of("^.*?pts_time:(-?\\d+(?:\\.\\d*)?)$");
	private static final Regex REGEX_VOLUME = Regex.of("^lavfi.astats.Overall.RMS_level=(-?\\d+(?:\\.\\d*)?)$");
	
	private final int port;
	private Consumer<AudioVolume> listener;
	private volatile Thread thread;
	private volatile Exception exception;
	
	private AudioVolume volume;
	private AudioVolume temp;
	
	private volatile ServerSocket server;
	private volatile Socket socket;
	
	public RealTimeAudio(int port) {
		this.port = checkPort(port);
	}
	
	private static final int checkPort(int port) {
		if(port < DRMConstants.PORT_MIN || port > DRMConstants.PORT_MAX) {
			throw new IllegalArgumentException();
		}
		
		return port;
	}
	
	private final void notifyListener(AudioVolume av) {
		if(listener != null) {
			listener.accept(av);
		}
	}
	
	private final void parseLine(String line) {
		Matcher matcher;
		
		if(!temp.isTimeValid()
				&& (matcher = REGEX_TIME.matcher(line)).matches()) {
			temp.time = Double.valueOf(matcher.group(1));
			
			if(temp.isValid()) {
				notifyListener(temp.setAndReset(volume));
			}
			
			return; // Line processed
		}
		
		if(!temp.isVolumeValid()
				&& (matcher = REGEX_VOLUME.matcher(line)).matches()) {
			temp.volume = Double.valueOf(matcher.group(1));
			
			if(temp.isValid()) {
				notifyListener(temp.setAndReset(volume));
			}
			
			return; // Line processed
		}
	}
	
	private final void execute() {
		try(ServerSocket s = new ServerSocket(port)) {
			server = s;
			
			if(logger.isDebugEnabled()) {
				logger.debug("Waiting for a socket...");
			}
			
			socket = server.accept();
			
			if(logger.isDebugEnabled()) {
				logger.debug("Socket connected");
			}
			
			try(BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
				for(String line; (line = reader.readLine()) != null; parseLine(line));
			}
		} catch(IOException ex) {
			exception = ex;
		}
	}
	
	public void listen(Consumer<AudioVolume> listener) throws Exception {
		volume = new AudioVolume();
		temp = new AudioVolume();
		this.listener = listener; // Can be null
		(thread = Threads.newThreadUnmanaged(this::execute)).start();
	}
	
	public void close() throws Exception {
		if(thread != null) {
			thread.interrupt();
			thread = null;
		}
		
		if(server != null) {
			server.close();
		}
		
		if(socket != null) {
			socket.close();
		}
		
		if(exception != null) {
			throw exception;
		}
	}
	
	public AudioVolume volume() {
		return volume;
	}
	
	public static final class AudioVolume {
		
		private double time;
		private double volume;
		
		private AudioVolume() {
			time = Double.NaN;
			volume = Double.NaN;
		}
		
		protected AudioVolume setAndReset(AudioVolume other) {
			other.time = time;
			other.volume = volume;
			time = Double.NaN;
			volume = Double.NaN;
			return other;
		}
		
		public AudioVolume time(double newTime) {
			AudioVolume copy = new AudioVolume();
			copy.time = newTime;
			copy.volume = volume;
			return copy;
		}
		
		public double time() {
			return time;
		}
		
		public double volume() {
			return volume;
		}
		
		protected boolean isTimeValid() {
			return !Double.isNaN(time);
		}
		
		protected boolean isVolumeValid() {
			return !Double.isNaN(volume);
		}
		
		protected boolean isValid() {
			return isTimeValid() && isVolumeValid();
		}
	}
}