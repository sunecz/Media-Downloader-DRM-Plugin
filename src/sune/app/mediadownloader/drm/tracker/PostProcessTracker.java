package sune.app.mediadownloader.drm.tracker;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PostProcessTracker extends TimeUpdatableTracker {
	
	private final List<String> names;
	private final String name;
	
	private PostProcessTracker(double totalTime, List<String> names, String name) {
		super(totalTime);
		this.names = names;
		this.name = name;
	}
	
	public List<String> names() {
		return names;
	}
	
	@Override
	public String state() {
		// TODO: Change
		return "POST_PROCESS::" + name;
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
		
		public PostProcessTracker create(double totalTime, E value) {
			String name = Objects.requireNonNull(value).name();
			if(!names.contains(name))
				throw new IllegalArgumentException("Name is not in all enum names");
			return new PostProcessTracker(totalTime, names, name);
		}
	}
}