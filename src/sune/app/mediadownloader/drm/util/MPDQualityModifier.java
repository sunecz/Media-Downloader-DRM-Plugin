package sune.app.mediadownloader.drm.util;

import java.util.Objects;
import java.util.function.Function;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import sune.app.mediadown.media.MediaQuality;
import sune.app.mediadown.media.MediaQuality.AudioQualityValue;
import sune.app.mediadown.media.MediaQuality.VideoQualityValue;
import sune.app.mediadown.media.MediaResolution;

public final class MPDQualityModifier {
	
	/*
	 * [-] Bandwidth approximation table for compressed audio with 2 channels
	 * and bit depth of 16.
	 * +---------+----------+-----------+
	 * | Quality | BitRate  | Bandwidth |
	 * +---------+----------+-----------+
	 * | HIGH    | 256 kbps |    256000 |
	 * | MEDIUM  | 128 kbps |    128000 |
	 * | LOW     |  96 kbps |     96000 |
	 * +---------+----------+-----------+
	 */
	private static final MediaQuality[] AUDIO_QUALITIES = {
		MediaQuality.AUDIO_LOW.withValue(new AudioQualityValue(96000, 0, 0)),
		MediaQuality.AUDIO_MEDIUM.withValue(new AudioQualityValue(128000, 0, 0)),
		MediaQuality.AUDIO_HIGH.withValue(new AudioQualityValue(256000, 0, 0)),
	};
	
	private static final MediaQuality[] VIDEO_QUALITIES = {
		MediaQuality.P240,
		MediaQuality.P480,
		MediaQuality.P720,
	};
	
	private final Document xml;
	
	private MPDQualityModifier(Document xml) {
		this.xml = Objects.requireNonNull(xml);
	}
	
	private static final MediaQuality audioQualityFromBandwidth(int bandwidth) {
		if(bandwidth <= 0) {
			return MediaQuality.UNKNOWN;
		}
		
		int index = Math.min((int) (bandwidth / 128000), AUDIO_QUALITIES.length - 1);
		MediaQuality quality = AUDIO_QUALITIES[index];
		return quality.withValue(new AudioQualityValue(bandwidth, 0, 0));
	}
	
	private static final MediaQuality audioQualityFromVideoQuality(MediaQuality videoQuality) {
		if(videoQuality.is(MediaQuality.UNKNOWN)) {
			return MediaQuality.UNKNOWN;
		}
		
		int videoHeight = ((VideoQualityValue) videoQuality.value()).height();
		
		for(int i = VIDEO_QUALITIES.length - 1; i >= 0; --i) {
			MediaQuality quality = VIDEO_QUALITIES[i];
			int height = ((VideoQualityValue) quality.value()).height();
			
			if(videoHeight >= height) {
				return AUDIO_QUALITIES[i];
			}
		}
		
		return AUDIO_QUALITIES[0];
	}
	
	private static final <T extends Comparable<T>> void removeRepresentations(Element adaptationSet, T wantedValue,
			Function<Element, T> transformer) {
		Elements representations = adaptationSet.getElementsByTag("Representation");
		
		// If there is only one or no representation there is nothing to be done
		if(representations.size() <= 1) {
			return;
		}
		
		Element selectedRepresentation = null;
		T aboveValue = null;
		T belowValue = null;
		
		for(Element representation : representations) {
			T value = transformer.apply(representation);
			
			if(value.compareTo(wantedValue) >= 0) {
				// Quality X with t(X) >= t(W) found, find the closest possible quality
				if(aboveValue == null || value.compareTo(aboveValue) <= 0) {
					aboveValue = value;
					selectedRepresentation = representation;
				}
			} else if(aboveValue == null) {
				// No quality X with t(X) >= t(W) found, find the best possible quality
				if(belowValue == null || value.compareTo(belowValue) >= 0) {
					belowValue = value;
					selectedRepresentation = representation;
				}
			}
			
			representation.remove();
		}
		
		adaptationSet.appendChild(selectedRepresentation);
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
			return audioQualityFromBandwidth(bandwidth);
		});
	}
	
	private final void modifyAdaptationSet(Element adaptationSet, MediaQuality videoQuality,
			MediaQuality audioQuality) {
		String type = adaptationSet.attr("mimeType");
		if(type.startsWith("video/")) modifyVideo(adaptationSet, videoQuality); else
		if(type.startsWith("audio/")) modifyAudio(adaptationSet, audioQuality); else
		throw new IllegalStateException("Unsupported mime type of adaptation set");
	}
	
	public final void modify(MediaQuality videoQuality, MediaQuality audioQuality) {
		for(Element adaptationSet : xml.getElementsByTag("AdaptationSet")) {
			modifyAdaptationSet(adaptationSet, videoQuality, audioQuality);
		}
	}
	
	public final void modify(MediaQuality videoQuality) {
		modify(videoQuality, audioQualityFromVideoQuality(videoQuality));
	}
	
	public final Document xml() {
		return xml;
	}
}