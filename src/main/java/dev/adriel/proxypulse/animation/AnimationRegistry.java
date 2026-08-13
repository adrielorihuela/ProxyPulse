package dev.adriel.proxypulse.animation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * All animations currently loaded, keyed by their lowercase name.
 *
 * <p>Parsing is deliberately forgiving: one malformed animation is reported and skipped instead of
 * aborting the whole file, because a single typo should not take every other animation on the
 * network down with it.
 */
public final class AnimationRegistry {

  private volatile Map<String, Animation> animations = Map.of();

  /**
   * Reads every animation from an {@code animations.yml} node.
   *
   * @return the problems found, one message per animation that had to be skipped
   */
  public List<String> load(final ConfigurationNode root) throws SerializationException {
    final Map<String, Animation> parsed = new LinkedHashMap<>();
    final List<String> problems = new ArrayList<>();

    for (final Map.Entry<Object, ? extends ConfigurationNode> entry : root.childrenMap()
        .entrySet()) {
      final String name = String.valueOf(entry.getKey());
      final ConfigurationNode node = entry.getValue();
      final List<String> texts = node.node("texts").getList(String.class);

      if (texts == null || texts.isEmpty()) {
        problems.add("animation '" + name + "' has no 'texts' list");
        continue;
      }
      try {
        final long interval = node.node("change-interval").getLong(Animation.MINIMUM_INTERVAL);
        parsed.put(name.toLowerCase(Locale.ROOT), new Animation(name, interval, texts));
      } catch (final IllegalArgumentException exception) {
        problems.add(exception.getMessage());
      }
    }

    // Insertion order is kept so that listing them back shows the file's own order.
    this.animations = java.util.Collections.unmodifiableMap(parsed);
    return List.copyOf(problems);
  }

  public Optional<Animation> find(final String name) {
    return Optional.ofNullable(animations.get(name.toLowerCase(Locale.ROOT)));
  }

  public Map<String, Animation> all() {
    return animations;
  }

  public int size() {
    return animations.size();
  }
}
