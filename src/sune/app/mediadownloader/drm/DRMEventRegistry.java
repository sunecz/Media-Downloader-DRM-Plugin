package sune.app.mediadownloader.drm;

import java.util.List;

import sune.app.mediadown.event.EventBindable;
import sune.app.mediadown.event.EventRegistry;
import sune.app.mediadown.event.EventType;
import sune.app.mediadown.event.IEventType;
import sune.app.mediadown.event.Listener;

public final class DRMEventRegistry {
	
	private final EventRegistry<IEventType> eventRegistry = new EventRegistry<>();
	
	@SuppressWarnings("unchecked")
	public final <E> void call(EventType<? extends IEventType, E> type) {
		eventRegistry.call((EventType<IEventType, E>) type);
	}
	
	@SuppressWarnings("unchecked")
	public final <E> void call(EventType<? extends IEventType, E> type, E value) {
		eventRegistry.call((EventType<IEventType, E>) type, value);
	}
	
	@SuppressWarnings("unchecked")
	public final <E> void add(EventType<? extends IEventType, E> type, Listener<E> listener) {
		eventRegistry.add((EventType<IEventType, E>) type, listener);
	}
	
	@SuppressWarnings("unchecked")
	public final <E> void remove(EventType<? extends IEventType, E> type, Listener<E> listener) {
		eventRegistry.remove((EventType<IEventType, E>) type, listener);
	}
	
	@SuppressWarnings("unchecked")
	public final <E> List<Listener<?>> getListeners(EventType<? extends IEventType, E> type) {
		return eventRegistry.getListeners().get((EventType<IEventType, E>) type);
	}
	
	@SuppressWarnings("unchecked")
	public final <T extends IEventType, E> void bindEvents(EventBindable<T> eventBindable, EventType<T, E> type) {
		List<Listener<?>> listeners = getListeners(type);
		if((listeners == null)) return; // Nothing to do
		for(Listener<?> listener : listeners) {
			eventBindable.addEventListener(type, (Listener<E>) listener);
		}
	}
}