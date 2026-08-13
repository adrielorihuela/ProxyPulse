package dev.adriel.proxypulse.session;

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
 */
public record TrackedElement(
    List<Component> originals,
    List<Component> lastSent,
    Function<List<Component>, Object> packetFactory,
    long expiresAt) {

  public static TrackedElement of(final List<Component> originals, final List<Component> rendered,
      final Function<List<Component>, Object> packetFactory, final long expiresAt) {
    return new TrackedElement(List.copyOf(originals), List.copyOf(rendered), packetFactory,
        expiresAt);
  }

  public TrackedElement withLastSent(final List<Component> rendered) {
    return new TrackedElement(originals, List.copyOf(rendered), packetFactory, expiresAt);
  }

  public boolean hasExpired(final long timestampMillis) {
    return expiresAt > 0 && timestampMillis >= expiresAt;
  }
}
