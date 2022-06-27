package sune.app.mediadownloader.drm.util;

import java.util.function.Function;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import sune.app.mediadown.media.MediaQuality;
import sune.app.mediadown.media.MediaResolution;
import sune.app.mediadownloader.drm.util.DRMUtils.AudioQuality;

public final class MPDQualityModifier {
	
	private final Document xml;
	
	MPDQualityModifier(Document xml) {
		this.xml = xml;
	}
	
	public static final MPDQualityModifier fromString(String content) {
		return new MPDQualityModifier(Jsoup.parse(content, "", Parser.xmlParser()));
	}
	
	private final <T extends Comparable<T>>void removeRepresentations(Element adaptSet, T defaultValue,
			T wantedValue, Function<Element, T> transformer) {
		Elements reprs = adaptSet.getElementsByTag("Representation");
		if(reprs.size() <= 1) return; // Nothing to do
		T bestQuality = defaultValue;
		Element bestRepr = null;
		for(Element repr : reprs) {
			T quality = transformer.apply(repr);
			if(quality.compareTo(bestQuality) > 0 && quality.compareTo(wantedValue) <= 0) {
				bestQuality = quality;
				bestRepr = repr;
			}
			repr.remove();
		}
		if(bestRepr != null) {
			adaptSet.appendChild(bestRepr);
		}
	}
	
	private final void modifyVideo(Element adaptSet, MediaQuality wantedQuality) {
		removeRepresentations(adaptSet, MediaQuality.UNKNOWN, wantedQuality, (repr) -> {
			int width = Integer.valueOf(repr.attr("width"));
			int height = Integer.valueOf(repr.attr("height"));
			return MediaQuality.fromResolution(new MediaResolution(width, height));
		});
	}
	
	private final void modifyAudio(Element adaptSet, MediaQuality wantedQuality) {
		AudioQuality wantedAudioQuality = AudioQuality.fromMediaQuality(wantedQuality);
		removeRepresentations(adaptSet, AudioQuality.UNKNOWN, wantedAudioQuality, (repr) -> {
			int bandwidth = Integer.valueOf(repr.attr("bandwidth"));
			double bitRate = bandwidth / 1000.0;
			return AudioQuality.fromBitRate(bitRate);
		});
	}
	
	private final void modifyAdaptationSet(Element adaptSet, MediaQuality wantedQuality) {
		String type = adaptSet.attr("mimeType");
		if(type.startsWith("video/")) modifyVideo(adaptSet, wantedQuality); else
		if(type.startsWith("audio/")) modifyAudio(adaptSet, wantedQuality); else
		throw new IllegalStateException("Unsupported mime type of adaptation set");
	}
	
	public final void modify(MediaQuality wantedQuality) {
		for(Element adaptSet : xml.getElementsByTag("AdaptationSet")) {
			modifyAdaptationSet(adaptSet, wantedQuality);
		}
	}
	
	public final Document xml() {
		return xml;
	}
}