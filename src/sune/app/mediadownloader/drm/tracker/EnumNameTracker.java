package sune.app.mediadownloader.drm.tracker;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import sune.app.mediadown.event.tracker.PlainTextTracker;

public class EnumNameTracker extends PlainTextTracker {
	
	private final List<String> names;
	private final String name;
	
	private EnumNameTracker(List<String> names, String name) {
		this.names = names;
		this.name = name;
	}
	
	public List<String> names() {
		return names;
	}
	
	@Override
	public String state() {
		// TODO: Change
		return "ENUM::" + name;
	}
	
	public String name() {
		return name;
	}
	
	public static final class Factory<E extends Enum<?>> {
		
		private final List<String> names;
		
		public Factory(Class<E> clazz) {
			this.names = Stream.of(Objects.requireNonNull(clazz).getEnumConstants())
					.map(Enum::name).collect(Collectors.toUnmodifiableList());
		}
		
		public EnumNameTracker create(E value) {
			String name = Objects.requireNonNull(value).name();
			if(!names.contains(name))
				throw new IllegalArgumentException("Name is not in all enum names");
			return new EnumNameTracker(names, name);
		}
	}
}