package dev.adriel.proxypulse.animation;

import java.util.List;
import java.util.Objects;

/**
 * A named sequence of text frames shown one after another.
 *
 * <p>Which frame is current is derived from the wall clock rather than from a counter this class
 * advances. Two consequences fall out of that, both wanted: every player on the network sees the
 * same frame at the same moment no matter which backend they are on, and a player who joins
 * mid-cycle picks the animation up where everyone else is instead of restarting it.
 */
public record Animation(String name, long changeInterval, List<String> frames) {

  public static final long MINIMUM_INTERVAL = 50L;

  public Animation {
    Objects.requireNonNull(name, "name");
    frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
    if (frames.isEmpty()) {
      throw new IllegalArgumentException("Animation '" + name + "' has no texts");
    }
    if (changeInterval < MINIMUM_INTERVAL) {
      throw new IllegalArgumentException("Animation '" + name + "' has a change-interval of "
          + changeInterval + "ms; the lowest a client can actually show is " + MINIMUM_INTERVAL);
    }
  }

  /** Index of the frame that should be on screen at the given instant. */
  public int frameIndexAt(final long timestampMillis) {
    return (int) Math.floorMod(timestampMillis / changeInterval, (long) frames.size());
  }

  /** Raw text of the frame that should be on screen at the given instant. */
  public String frameAt(final long timestampMillis) {
    return frames.get(frameIndexAt(timestampMillis));
  }
}
