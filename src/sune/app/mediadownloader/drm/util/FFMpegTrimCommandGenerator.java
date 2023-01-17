package sune.app.mediadownloader.drm.util;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

public final class FFMpegTrimCommandGenerator {
	
	private final Function<Cut.OfDouble, Cut.OfLong> transform;
	private final String cmdSegmentTrim;
	private final String concatArgs;
	private final String nameSuffix;
	
	private FFMpegTrimCommandGenerator(Function<Cut.OfDouble, Cut.OfLong> transform, String streamDescriptor,
			String fncTrimName, String argTrimStart, String argTrimEnd, String argSetPTS, String concatArgs,
			String nameSuffix) {
		this.transform = transform;
		this.cmdSegmentTrim = DRMUtils.format(
			"[%s]%s=%s=%%.6f:%s=%%.6f,%s[t%s%%d];",
			streamDescriptor, fncTrimName, argTrimStart, argTrimEnd, argSetPTS, nameSuffix
		);
		this.concatArgs = concatArgs;
		this.nameSuffix = nameSuffix;
	}
	
	private static final Function<Cut.OfDouble, Cut.OfLong> fMultiply(double mult) {
		return ((cut) -> new Cut.OfLong((long) Math.round(cut.start() * mult), (long) Math.round(cut.end() * mult)));
	}
	
	private static final Function<Cut.OfDouble, Cut.OfLong> fMultiply(int mult) {
		return ((cut) -> new Cut.OfLong((long) Math.round(cut.start() * mult), (long) Math.round(cut.end() * mult)));
	}
	
	public static final FFMpegTrimCommandGenerator forVideo(double frameRate) {
		return new FFMpegTrimCommandGenerator(fMultiply(frameRate), "0:v", "trim", "start", "end",
		                                      "setpts=PTS-STARTPTS", "v=1:a=0", "v");
	}
	
	public static final FFMpegTrimCommandGenerator forAudio(int sampleRate) {
		return new FFMpegTrimCommandGenerator(fMultiply(sampleRate), "0:a", "atrim", "start", "end",
		                                      "asetpts=PTS-STARTPTS", "v=0:a=1", "a");
	}
	
	private final TrimCommand commandTrim(int commandCtr, List<Cut.OfDouble> cuts) {
		StringBuilder builder = new StringBuilder();
		int numOfCuts = cuts.size();
		
		// Generate the trim part of the command
		for(int i = 0; i < numOfCuts; ++i) {
			Cut.OfDouble cut = cuts.get(i);
			builder.append(DRMUtils.format(cmdSegmentTrim, cut.start(), cut.end(), i));
		}
		
		builder.deleteCharAt(builder.length() - 1);
		
		// Generate the concat part of the command
		String concatMap = "t" + nameSuffix;
		if(numOfCuts >= 2) {
			builder.append(';');
			IntStream.range(0, numOfCuts)
				.mapToObj((i) -> "[t" + nameSuffix + i + "]")
				.forEach(builder::append);
			builder.append(DRMUtils.format("concat=n=%d:%s[c%s]", numOfCuts, concatArgs, nameSuffix));
			concatMap = "c" + nameSuffix;
		}
		
		return new TrimCommand(builder.toString(), concatMap);
	}
	
	public TrimCommand command(List<Cut.OfDouble> cutsInclude) {
		return commandTrim(0, cutsInclude);
	}
	
	public static final class TrimCommand {
		
		private final String script;
		private final String map;
		
		public TrimCommand(String script, String map) {
			this.script = script;
			this.map = map;
		}
		
		public String script() {
			return script;
		}
		
		public String map() {
			return map;
		}
	}
}