package sune.app.mediadownloader.drm.util;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import sune.app.mediadown.media.MediaQuality;
import sune.app.mediadown.media.MediaQuality.AudioQualityValue;
import sune.app.mediadown.media.MediaResolution;
import sune.app.mediadown.media.MediaType;

public final class MPDQualityModifier {
	
	private final Document xml;
	
	private MPDQualityModifier(Document xml) {
		this.xml = Objects.requireNonNull(xml);
	}
	
	private static final MediaQuality audioQualityFromBitRate(int bitRate) {
		if(bitRate <= 0.0) {
			return MediaQuality.UNKNOWN;
		}
		
		AudioQualityValue value = new AudioQualityValue(0, 0, bitRate);
		return Stream.of(MediaQuality.validQualities())
				     .filter((q) -> q.mediaType().is(MediaType.AUDIO))
				     .sorted(MediaQuality.reversedComparatorKeepOrder())
				     .filter((q) -> Integer.compare(value.bitRate(), ((AudioQualityValue) q.value()).bitRate()) >= 0)
				     .findFirst().orElse(MediaQuality.UNKNOWN);
	}
	
	private static final <T extends Comparable<T>> void removeRepresentations(Element adaptationSet, T wantedValue,
			Function<Element, T> transformer) {
		Elements representations = adaptationSet.getElementsByTag("Representation");
		
		// If there is only one or no representation there is nothing to be done
		if(representations.size() <= 1) {
			return;
		}
		
		T bestValue = null;
		Element bestRepresentation = null;
		
		for(Element representation : representations) {
			T value = transformer.apply(representation);
			
			if(bestValue == null
					|| (value.compareTo(bestValue) > 0 && value.compareTo(wantedValue) <= 0)) {
				bestValue = value;
				bestRepresentation = representation;
			}
			
			representation.remove();
		}
		
		adaptationSet.appendChild(bestRepresentation);
	}
	
	public static final MPDQualityModifier fromString(String string) {
		return new MPDQualityModifier(Jsoup.parse(string, "", Parser.xmlParser()));
	}
	
	private final void modifyVideo(Element adaptationSet, MediaQuality wantedQuality) {
		removeRepresentations(adaptationSet, wantedQuality, (representation) -> {
			int width = Integer.valueOf(representation.attr("width"));
			int height = Integer.valueOf(representation.attr("height"));
			return MediaQuality.fromResolution(new MediaResolution(width, height));
		});
	}
	
	private final void modifyAudio(Element adaptationSet, MediaQuality wantedQuality) {
		removeRepresentations(adaptationSet, wantedQuality, (representation) -> {
			int bandwidth = Integer.valueOf(representation.attr("bandwidth"));
			int bitRate = bandwidth / 1000;
			return audioQualityFromBitRate(bitRate);
		});
	}
	
	private final void modifyAdaptationSet(Element adaptationSet, MediaQuality wantedQuality) {
		String type = adaptationSet.attr("mimeType");
		if(type.startsWith("video/")) modifyVideo(adaptationSet, wantedQuality); else
		if(type.startsWith("audio/")) modifyAudio(adaptationSet, wantedQuality); else
		throw new IllegalStateException("Unsupported mime type of adaptation set");
	}
	
	public final void modify(MediaQuality wantedQuality) {
		for(Element adaptationSet : xml.getElementsByTag("AdaptationSet")) {
			modifyAdaptationSet(adaptationSet, wantedQuality);
		}
	}
	
	public final Document xml() {
		return xml;
	}
}