package sune.app.mediadownloader.drm.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import sune.app.mediadown.util.Utils;

public final class FFMpegTrimCommandGenerator {
	
	// Operating systems have some maximum command length that can be passed as an argument
	// to an OS-specific function to create a process, such as CreateProcess in Windows or
	// exec in Unix-like operating systems.
	// Due to this limitation we have to define some common minimum length that can be used
	// for all supported operating systems, currently Windows, Unix and MacOS X.
	// Based on some answers on the internet, we can assume that the lengths will be similar
	// for each user when the default settings are used:
	//     - Windows  = 32767 (CreateProcess function, lpCommandLine argument)
	//     - Unix     ~ (128 to 512) * 1024 ~ 131072 to 524288 (e.g. see https://unix.stackexchange.com/a/120842)
	//     - Mac OS X ~ (256 to 512) * 1024 ~ 262144 to 524288 (e.g. see https://serverfault.com/a/69450)
	// From these numbers we select the minimum, which is 32767, and use it for the implementation,
	// regardless of the operating system.
	// However, since the command generation is dynamic and our estimation is only
	// an approximation, we have to ensure that the command still fits into this limit.
	// Therefore we select some safe limit that is lower than the actual limit.
	private static final int MAX_LENGTH = 30000;

	private final Path input;
	private final Path output;
	private final Function<Cut.OfDouble, Cut.OfLong> transform;
	private final String cmdSegmentTrim;
	private final String argsCommandTrim;
	private final String concatArgs;
	private final String streamType;
	
	private FFMpegTrimCommandGenerator(Path input, Path output, Function<Cut.OfDouble, Cut.OfLong> transform,
			String streamDescriptor, String fncTrimName, String argTrimStart, String argTrimEnd, String argSetPTS,
			String concatArgs, String argsCommandTrim, String streamType) {
		this.input = input;
		this.output = output;
		this.transform = transform;
		this.cmdSegmentTrim = String.format("[%s]%s=%s=%%d:%s=%%d,%s[t%%d];", streamDescriptor, fncTrimName,
		                                    argTrimStart, argTrimEnd, argSetPTS);
		this.argsCommandTrim = argsCommandTrim;
		this.concatArgs = concatArgs;
		this.streamType = streamType;
	}
	
	private static final Function<Cut.OfDouble, Cut.OfLong> fMultiply(double mult) {
		return ((cut) -> new Cut.OfLong((long) Math.round(cut.start() * mult), (long) Math.round(cut.end() * mult)));
	}
	
	private static final Function<Cut.OfDouble, Cut.OfLong> fMultiply(int mult) {
		return ((cut) -> new Cut.OfLong((long) Math.round(cut.start() * mult), (long) Math.round(cut.end() * mult)));
	}
	
	public static final FFMpegTrimCommandGenerator forVideo(Path input, Path output, double frameRate, String additionalArgs) {
		return new FFMpegTrimCommandGenerator(input, output, fMultiply(frameRate), "0:v", "trim", "start_frame", "end_frame",
		                                      "setpts=N/FR/TB", "v=1:a=0", " -an " + additionalArgs, "v");
	}
	
	public static final FFMpegTrimCommandGenerator forAudio(Path input, Path output, int sampleRate, String additionalArgs) {
		return new FFMpegTrimCommandGenerator(input, output, fMultiply(sampleRate), "0:a", "atrim", "start_sample", "end_sample",
		                                      "asetpts=N/SR/TB", "v=0:a=1", " -vn " + additionalArgs, "a");
	}
	
	private final double lengthPerTrim() {
		final double lenCtrStr = 2.890; // ctr = <0, 999> (average)
		final double lenValStr = 10.8889; // val = <0, 99999999999> (average)
		double lenSingleTrim = (cmdSegmentTrim.length() - 3/* args num */ * 2) + 2 * lenValStr + 1 * lenCtrStr;
		double lenSingleConcat = 3.0 + lenCtrStr; // str = "[ti]", where i is ctr
		return lenSingleTrim + lenSingleConcat;
	}
	
	private final int maxNumberOfCutsPerTrim() {
		return (int) (MAX_LENGTH / lengthPerTrim());
	}
	
	private final Path trimOutputPath(int commandCtr) {
		String fullName = output.getFileName().toString();
		String fileType = Utils.fileType(fullName);
		String fileName = Utils.fileNameNoType(fullName);
		return output.resolveSibling(fileName + ".trim" + commandCtr + "." + fileType);
	}
	
	private final String commandTrim(int commandCtr, Iterator<Cut.OfDouble> it, int numOfCuts, boolean singleCommand) {
		// Generate the trim part of the command
		StringBuilder trimBuilder = new StringBuilder();
		for(int i = 0; i < numOfCuts; ++i) {
			Cut.OfLong cut = transform.apply(it.next());
			trimBuilder.append(DRMUtils.format(cmdSegmentTrim, cut.start(), cut.end(), i));
		}
		String trimString = trimBuilder.substring(0, trimBuilder.length() - 1).toString();
		// Generate the concat part of the command
		String concatMap = "t0";
		String concatString = "";
		if(numOfCuts >= 2) {
			StringBuilder concatBuilder = new StringBuilder();
			IntStream.range(0, numOfCuts)
				.mapToObj((i) -> "[t" + i + "]")
				.forEach(concatBuilder::append);
			concatString = ";" + String.format("%sconcat=n=%d:%s[c]", concatBuilder.toString(), numOfCuts, concatArgs);
			concatMap = "c";
		}
		// Path to a temporary file for later join
		Path outputPath = !singleCommand ? trimOutputPath(commandCtr) : output;
		// Build the whole command
		StringBuilder builder = new StringBuilder();
		builder.append(" -y -hide_banner -v info");
		builder.append(" -i \"%{input}s\"");
		builder.append(" -filter_complex \"%{trim}s%{concat}s\"");
		builder.append(" -map [%{concatMap}s]");
		builder.append(argsCommandTrim);
		builder.append(" \"%{output}s\"");
		String command = Utils.format(builder.toString(),
			"input", input.toAbsolutePath().toString(),
			"output", outputPath.toAbsolutePath().toString(),
			"trim", trimString,
			"concat", concatString,
			"concatMap", concatMap);
		return command;
	}
	
	private final String commandJoin(int numOfTrimCommands) {
		// Build the inputs part of the command
		StringBuilder inputsBuilder = new StringBuilder();
		IntStream.range(0, numOfTrimCommands)
			.mapToObj((i) -> " -i \"" + trimOutputPath(i).toAbsolutePath().toString() + "\"")
			.forEach(inputsBuilder::append);
		String inputsString = inputsBuilder.toString();
		// Build the concat part of the command
		StringBuilder concatBuilder = new StringBuilder();
		IntStream.range(0, numOfTrimCommands)
			.mapToObj((i) -> "[" + i + ":" + streamType + "]")
			.forEach(concatBuilder::append);
		String concatString = String.format("%sconcat=n=%d:%s[c]", concatBuilder.toString(), numOfTrimCommands, concatArgs);
		// Build the whole command
		StringBuilder builder = new StringBuilder();
		builder.append(" -y -hide_banner -v info");
		builder.append(inputsString);
		builder.append(" -filter_complex \"%{concat}s\"");
		builder.append(" -map [c]");
		builder.append(argsCommandTrim);
		builder.append(" \"%{output}s\"");
		String command = Utils.format(builder.toString(),
			"output", output.toAbsolutePath().toString(),
			"concat", concatString);
		return command;
	}
	
	public List<String> commands(List<Cut.OfDouble> cutsInclude) {
		List<String> commands = new ArrayList<>();
		final int numOfCuts = cutsInclude.size();
		final int maxNumOfCutsPerTrim = maxNumberOfCutsPerTrim();
		final int numOfTrimCommands = (int) Math.ceil((double) numOfCuts / maxNumOfCutsPerTrim);
		Iterator<Cut.OfDouble> it = cutsInclude.iterator();
		if(numOfTrimCommands > 1) {
			int numOfCutsLeft = numOfCuts; 
			for(int i = 0; i < numOfTrimCommands; ++i) {
				int count = Math.min(maxNumOfCutsPerTrim, numOfCutsLeft);
				commands.add(commandTrim(i, it, count, false));
				numOfCutsLeft -= count;
			}
			commands.add(commandJoin(numOfTrimCommands));
		} else {
			commands.add(commandTrim(0, it, numOfCuts, true));
		}
		return commands;
	}
}