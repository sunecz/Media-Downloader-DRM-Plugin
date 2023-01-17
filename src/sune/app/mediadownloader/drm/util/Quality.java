package sune.app.mediadownloader.drm.util;

import java.util.stream.Stream;

public enum Quality {
	
	LOSSLESS {
		
		@Override
		public void recordCommandArguments(StringBuilder builder) {
			builder.append(" -c:v libx264rgb -qp 0 -pix_fmt rgb24 -g 1"); // Output video settings
			builder.append(" -c:a pcm_s16le"); // Output audio settings
		}
		
		@Override
		public String videoCommandArguments() {
			return "-c:v libx264rgb -preset ultrafast -tune film -qp 0 -pix_fmt rgb24 -g 1";
		}
		
		@Override
		public String audioCommandArguments() {
			return "-c:a aac -b:a 320k";
		}
	},
	HIGH {
		
		@Override
		public void recordCommandArguments(StringBuilder builder) {
			builder.append(" -c:v libx264rgb -crf 0 -pix_fmt rgb24"); // Output video settings
			builder.append(" -c:a pcm_s16le"); // Output audio settings
		}
		
		@Override
		public String videoCommandArguments() {
			return "-c:v libx264rgb -preset ultrafast -tune film -crf 0 -pix_fmt rgb24";
		}
		
		@Override
		public String audioCommandArguments() {
			return "-c:a aac -b:a 256k";
		}
	},
	MEDIUM {
		
		@Override
		public void recordCommandArguments(StringBuilder builder) {
			builder.append(" -c:v libx264 -crf 17 -pix_fmt yuv420p"); // Output video settings
			builder.append(" -c:a aac"); // Output audio settings
		}
		
		@Override
		public String videoCommandArguments() {
			return "-c:v libx264 -preset ultrafast -tune film -crf 17 -pix_fmt yuv420p";
		}
		
		@Override
		public String audioCommandArguments() {
			return "-c:a aac -b:a 160k";
		}
	},
	LOW {
		
		@Override
		public void recordCommandArguments(StringBuilder builder) {
			builder.append(" -c:v libx264 -crf 23 -pix_fmt yuv420p"); // Output video settings
			builder.append(" -c:a aac"); // Output audio settings
		}
		
		@Override
		public String videoCommandArguments() {
			return "-c:v libx264 -preset ultrafast -tune film -crf 23 -pix_fmt yuv420p";
		}
		
		@Override
		public String audioCommandArguments() {
			return "-c:a aac -b:a 128k";
		}
	};
	
	public abstract void recordCommandArguments(StringBuilder builder);
	public abstract String videoCommandArguments();
	public abstract String audioCommandArguments();
	
	// <---- For configuration purposes
	
	private static Quality[] validValues;
	
	public static final Quality[] validValues() {
		if(validValues == null) {
			validValues = values();
		}
		
		return validValues;
	}
	
	public static final Quality of(String string) {
		if(string == null || string.isBlank()) {
			return null;
		}
		
		String normalized = string.strip().toUpperCase();
		return Stream.of(values())
				     .filter((v) -> normalized.equals(v.name()))
				     .findFirst().orElse(null);
	}
	
	@Override
	public String toString() {
		return super.toString().toLowerCase();
	}
	
	// ---->
}