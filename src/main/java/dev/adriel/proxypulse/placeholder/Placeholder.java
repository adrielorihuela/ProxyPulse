package dev.adriel.proxypulse.placeholder;

/**
 * Resolves one placeholder to the text that should take its place.
 *
 * <p>The argument is whatever followed the colon, so {@code %server_online:lobby%} arrives here as
 * key {@code server_online} and argument {@code lobby}. It is null when no colon was used.
 */
@FunctionalInterface
public interface Placeholder {

  /**
   * Resolves this placeholder.
   *
   * @return the replacement text, or null to leave the placeholder untouched on screen
   */
  String resolve(String argument, PulseContext context);
}
