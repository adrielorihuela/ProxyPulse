package dev.adriel.proxypulse.session;

import dev.adriel.proxypulse.animation.Animation;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;

/**
 * One piece of text on a player's screen that has to keep moving.
 *
 * <p>Only the original text is kept, exactly as the backend wrote it: that is what stays true
 * between frames, while a rendered copy is out of date the moment the clock moves. Rebuilding the
 * packet is left to a factory supplied by whoever tracked the element, so every kind of element --
 * a two component header and footer, a one component title, a boss bar tied to a uuid -- fits the
 * same shape.
 *
 * @param originals the text as the backend sent it, placeholders intact
 * @param lastSent what was last put on the wire, so identical frames are not re-sent
 * @param packetFactory builds the packet to send from freshly rendered text
 * @param expiresAt when the client would have dropped this text on its own, or 0 to keep it
 *     refreshing until the backend replaces it
 * @param animations the animations this text uses, worked out once when it was first seen
 * @param lastSignature what the inputs looked like last time it was rendered
 */
public record TrackedElement(
    List<Component> originals,
    List<Component> lastSent,
    Function<List<Component>, Object> packetFactory,
    long expiresAt,
    List<Animation> animations,
    long lastSignature) {

  public static TrackedElement of(final List<Component> originals, final List<Component> rendered,
      final Function<List<Component>, Object> packetFactory, final long expiresAt,
      final List<Animation> animations) {
    return new TrackedElement(List.copyOf(originals), List.copyOf(rendered), packetFactory,
        expiresAt, List.copyOf(animations), Long.MIN_VALUE);
  }

  public TrackedElement rendered(final List<Component> sent, final long signature) {
    return new TrackedElement(originals, List.copyOf(sent), packetFactory, expiresAt, animations,
        signature);
  }

  public boolean hasExpired(final long timestampMillis) {
    return expiresAt > 0 && timestampMillis >= expiresAt;
  }

  /**
   * A cheap summary of everything that could change what this element renders to.
   *
   * <p>Re-rendering twenty times a second is wasted work whenever nothing has actually moved: the
   * frames of the animations involved have not advanced and no time-based placeholder is due for a
   * refresh yet. Comparing this number first turns the common tick into an integer comparison.
   *
   * @param refreshBucket which refresh interval the current instant falls into
   */
  public long signature(final long timestampMillis, final long refreshBucket) {
    long signature = refreshBucket;
    for (final Animation animation : animations) {
      signature = signature * 31L + animation.frameIndexAt(timestampMillis);
    }
    return signature;
  }
}
