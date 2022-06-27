package sune.app.mediadownloader.drm.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.IEventType;
import sune.app.mediadown.util.Reflection;
import sune.app.mediadownloader.drm.DRMEventRegistry;

public final class DRMEventUtils {
	
	// Forbid anyone to create an instance of this class
	private DRMEventUtils() {
	}
	
	@SuppressWarnings("unchecked")
	public static final <E extends IEventType> EventType<E, Object>[] values(Class<E> clazz) {
		try {
			Method method = clazz.getDeclaredMethod("values");
			// The method has to be a static method
			if((Modifier.isStatic(method.getModifiers()))) {
				Reflection.setAccessible(method, true);
				return (EventType<E, Object>[]) method.invoke(null);
			}
		} catch(Exception ex) {
			throw new IllegalStateException("Unable to get all event type values form the given class: " + clazz);
		}
		return null;
	}
	
	public static final <E extends IEventType> void bind(Class<E> clazz, DRMEventRegistry source, DRMEventRegistry target) {
		for(EventType<E, Object> event : values(clazz)) {
			target.add(event, (data) -> source.call(event, data));
		}
	}
}