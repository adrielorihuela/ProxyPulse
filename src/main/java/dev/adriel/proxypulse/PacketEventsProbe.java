package dev.adriel.proxypulse;

import com.github.retrooper.packetevents.PacketEvents;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.retrooper.packetevents.velocity.factory.VelocityPacketEventsBuilder;
import java.nio.file.Path;
import org.slf4j.Logger;

/**
 * One-shot check that tells us whether the bundled PacketEvents build can hook into Velocity 4.
 *
 * <p>PacketEvents attaches by reflecting into proxy internals ({@code ConnectedPlayer} and the
 * Netty channel behind it), which Velocity 4 is free to have moved. Rather than discovering that
 * halfway through the packet rewriting work, the probe runs on its own and reports loudly either
 * way.
 */
final class PacketEventsProbe {

  private final ProxyServer proxy;
  private final Logger logger;
  private final Path dataDirectory;

  PacketEventsProbe(final ProxyServer proxy, final Logger logger, final Path dataDirectory) {
    this.proxy = proxy;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  void run() {
    try {
      final PluginContainer container = proxy.getPluginManager().getPlugin("proxypulse")
          .orElseThrow(() -> new IllegalStateException("ProxyPulse is not registered as a plugin"));

      PacketEvents.setAPI(
          VelocityPacketEventsBuilder.build(proxy, container, logger, dataDirectory));
      PacketEvents.getAPI().load();
      PacketEvents.getAPI().init();
      logger.info("[probe] PacketEvents attached successfully: {}",
          PacketEvents.getAPI().getVersion());
    } catch (final Throwable throwable) {
      logger.error("[probe] PacketEvents could NOT attach to this proxy. ProxyPulse would have to "
          + "fall back to its own Netty interception layer.", throwable);
    }
  }
}
