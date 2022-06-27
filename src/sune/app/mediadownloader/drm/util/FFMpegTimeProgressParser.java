package sune.app.mediadownloader.drm.util;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;

import sune.app.mediadown.util.Utils;
import sune.app.mediadownloader.drm.DRMLog;
import sune.app.mediadownloader.drm.tracker.TimeUpdatableTracker;

public class FFMpegTimeProgressParser implements Consumer<String> {
	
	private static final Logger logger = DRMLog.get();
	private static final Pattern PATTERN = Pattern.compile("^(?:frame|size)=.*?time=(.*?)\\s.*$");
	
	private final TimeUpdatableTracker tracker;
	
	public FFMpegTimeProgressParser(TimeUpdatableTracker tracker) {
		this.tracker = tracker;
	}
	
	@Override
	public void accept(String line) {
		if(logger.isDebugEnabled())
			logger.debug("FFMpeg | {}", line);
		Matcher matcher = PATTERN.matcher(line);
		if(!matcher.matches()) return; // Not a progress info
		String time = matcher.group(1);
		tracker.update(Utils.convertToSeconds(time));
	}
}