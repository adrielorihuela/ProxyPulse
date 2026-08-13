package dev.adriel.proxypulse.placeholder;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.adriel.proxypulse.config.PulseConfig;

/**
 * Everything a placeholder needs to resolve itself for one particular piece of text.
 *
 * <p>Viewer and subject are separate because the global tab list needs both: the header is about
 * the player looking at the list, while each row is about the player that row represents.
 * Everywhere else the two are the same player.
 */
public record PulseContext(
    ProxyServer proxy,
    PulseConfig config,
    Player viewer,
    Player subject,
    long timestampMillis) {

  public static PulseContext of(final ProxyServer proxy, final PulseConfig config,
      final Player viewer, final long timestampMillis) {
    return new PulseContext(proxy, config, viewer, viewer, timestampMillis);
  }

  /** The same context, but describing a different player -- used for one tab list row. */
  public PulseContext about(final Player otherSubject) {
    return new PulseContext(proxy, config, viewer, otherSubject, timestampMillis);
  }
}
