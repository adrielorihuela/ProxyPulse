package dev.adriel.proxypulse.session;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import dev.adriel.proxypulse.ProxyPulse;
import dev.adriel.proxypulse.animation.AnimationTicker;
import dev.adriel.proxypulse.net.PacketIds;
import dev.adriel.proxypulse.placeholder.PlaceholderEngine;
import dev.adriel.proxypulse.placeholder.PulseContext;
import io.netty.buffer.ByteBuf;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;

/**
 * Everything ProxyPulse knows about one connected player.
 *
 * <p>The session keeps the text a backend sent <em>as it was written</em>, placeholders and all.
 * That original is the only thing worth remembering: the rendered version is stale the moment the
 * next frame comes round, while the original stays valid for as long as the element is on screen.
 * Every tick the session re-renders from that original and, only if the result actually changed,
 * sends the packet again.
 */
public final class PlayerSession implements AnimationTicker.Refreshable {

  private final ProxyPulse plugin;
  private final Player player;
  private final MinecraftConnection connection;
  private final PacketIds packetIds;

  /** Header and footer exactly as the backend wrote them, or null while nothing is animated. */
  private final AtomicReference<HeaderFooter> trackedHeaderFooter = new AtomicReference<>();

  public PlayerSession(final ProxyPulse plugin, final Player player,
      final MinecraftConnection connection) {
    this.plugin = plugin;
    this.player = player;
    this.connection = connection;
    this.packetIds = PacketIds.forVersion(player.getProtocolVersion());
  }

  private record HeaderFooter(Component header, Component footer, Component lastSentHeader,
      Component lastSentFooter) {
  }

  public Player player() {
    return player;
  }

  /** Rewrites one outgoing message, or returns it untouched when there is nothing to do. */
  public Object rewrite(final Object message) {
    if (message instanceof ByteBuf buffer) {
      return rewriteRaw(buffer);
    }
    return message;
  }

  private Object rewriteRaw(final ByteBuf buffer) {
    final int start = buffer.readerIndex();
    final int packetId = ProtocolUtils.readVarInt(buffer);

    if (packetId != packetIds.headerAndFooter()) {
      buffer.readerIndex(start);
      return buffer;
    }

    final ProtocolVersion version = player.getProtocolVersion();
    final Component header = ComponentHolder.read(buffer, version).getComponent();
    final Component footer = ComponentHolder.read(buffer, version).getComponent();
    buffer.readerIndex(start);

    if (!PlaceholderEngine.mayContainPlaceholders(header)
        && !PlaceholderEngine.mayContainPlaceholders(footer)) {
      // Nothing of ours in there. Forget any previous tracking -- the backend has replaced the
      // animated header with a static one and it must be allowed to stay static.
      trackedHeaderFooter.set(null);
      return buffer;
    }

    final long now = System.currentTimeMillis();
    final Component renderedHeader = render(header, now);
    final Component renderedFooter = render(footer, now);
    trackedHeaderFooter.set(new HeaderFooter(header, footer, renderedHeader, renderedFooter));

    buffer.release();
    debug("rewriting tab list header/footer");
    return HeaderAndFooterPacket.create(renderedHeader, renderedFooter, version);
  }

  @Override
  public void refresh(final long timestampMillis) {
    final HeaderFooter tracked = trackedHeaderFooter.get();
    if (tracked == null || !connection.getChannel().isActive()) {
      return;
    }

    final Component header = render(tracked.header(), timestampMillis);
    final Component footer = render(tracked.footer(), timestampMillis);
    // Re-sending identical text every tick would be pure bandwidth: only a real change goes out.
    if (header.equals(tracked.lastSentHeader()) && footer.equals(tracked.lastSentFooter())) {
      return;
    }

    trackedHeaderFooter.set(new HeaderFooter(tracked.header(), tracked.footer(), header, footer));
    connection.write(HeaderAndFooterPacket.create(header, footer, player.getProtocolVersion()));
  }

  private Component render(final Component component, final long timestampMillis) {
    return plugin.placeholders().render(component,
        PulseContext.of(plugin.proxy(), plugin.config(), player, timestampMillis));
  }

  public void reportFailure(final Throwable throwable) {
    plugin.logger().warn("Failed to rewrite a packet for {}; it was sent untouched.",
        player.getUsername(), throwable);
  }

  private void debug(final String message) {
    if (plugin.config().debug()) {
      plugin.logger().info("[debug] {}: {}", player.getUsername(), message);
    }
  }
}
