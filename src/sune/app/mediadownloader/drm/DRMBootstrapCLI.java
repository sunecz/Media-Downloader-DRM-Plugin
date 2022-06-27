package sune.app.mediadownloader.drm;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.IEventType;
import sune.app.mediadownloader.drm.event.WidevineCDMEvent;

public final class DRMBootstrapCLI {
	
	private static final String LINE_PREFIX = "[DRMBootstrapCLI]";
	private static Map<EventType<?, ?>, String> eventTypeNames;
	
	// Forbid anyone to create an instance of this class
	private DRMBootstrapCLI() {
	}
	
	private static final <T extends IEventType> String eventTypeName(Class<T> clazz, EventType<T, ?> value) {
		if(eventTypeNames == null) {
			eventTypeNames = new HashMap<>();
			for(Field field : clazz.getDeclaredFields()) {
				try {
					eventTypeNames.put((EventType<?, ?>) field.get(null), field.getName());
				} catch(Exception ex) {
					// Ignore
				}
			}
		}
		return eventTypeNames.get(value);
	}
	
	private static final <P> void addListener(DRMInstance instance, String prefix, EventType<WidevineCDMEvent, P> type,
			Map<String, Function<P, Object>> map) {
		String name = eventTypeName(WidevineCDMEvent.class, type);
		instance.addEventListener(type, (o) -> {
			int size = 2 + map.size();
			List<String> formats = new ArrayList<>(size);
			List<Object> objects = new ArrayList<>(size);
			formats.add("%s");
			objects.add(prefix);
			formats.add("%s");
			objects.add(name);
			if(!map.isEmpty()) {
				map.entrySet().forEach((entry) -> {
					formats.add(entry.getKey());
					objects.add(entry.getValue().apply(o));
				});
			}
			String string = formats.stream().reduce("", (a, b) -> a + " " + b).stripLeading() + "\n";
			System.out.printf(Locale.US, string, objects.toArray());
		});
	}
	
	private static final String exceptionToString(Exception exception) {
		try(StringWriter string = new StringWriter();
			PrintWriter writer = new PrintWriter(string)) {
			exception.printStackTrace(writer);
			return string.toString();
		} catch(IOException ex) {
			// Ignore
		}
		return ""; // Return empty string to avoid null
	}
	
	private static final void addDefaultListeners(DRMInstance instance) {
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.BEGIN, Map.of());
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.WAIT_CEF_REQUEST, Map.of());
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.END, Map.of());
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.ERROR, Map.of("%s", (o) -> "\n" + exceptionToString(o.b) + "\n" + LINE_PREFIX + " "));
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.BEGIN_REQUEST, Map.of());
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.END_REQUEST, Map.of());
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.BEGIN_DOWNLOAD, Map.of());
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.UPDATE_DOWNLOAD, Map.of("%f", (o) -> o.b.getTracker().getProgress() * 100.0));
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.END_DOWNLOAD, Map.of());
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.BEGIN_EXTRACT, Map.of());
		addListener(instance, LINE_PREFIX, WidevineCDMEvent.END_EXTRACT, Map.of());
	}
	
	// This method is run with Media Downloader already initialized, no bootstrap needed.
	public static void run(String[] args) throws Exception {
		boolean downloadWidevineCDM = false;
		
		if(args.length > 0) {
			for(int i = 0, l = args.length; i < l; ++i) {
				String arg = args[i];
				switch(arg) {
					case "--download-widevine-cdm":
						downloadWidevineCDM = true;
						break;
					default:
						// Ignore
						break;
				}
			}
		}
		
		if(downloadWidevineCDM && !WidevineCDM.isInstalled()) {
			DRMInstance instance = DRMInstance.downloadWidevineDRM();
			addDefaultListeners(instance);
			instance.startAndWait();
		}
	}
	
	public static final String linePrefix() {
		return LINE_PREFIX;
	}
}