package dev.adriel.proxypulse.session;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.title.GenericTitlePacket;
import dev.adriel.proxypulse.ProxyPulse;
import dev.adriel.proxypulse.animation.AnimationTicker;
import dev.adriel.proxypulse.net.PacketIds;
import dev.adriel.proxypulse.placeholder.PlaceholderEngine;
import dev.adriel.proxypulse.placeholder.PulseContext;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

  private static final String KEY_TABLIST = "tablist";
  private static final String KEY_TITLE = "title";
  private static final String KEY_SUBTITLE = "subtitle";
  private static final String KEY_ACTION_BAR = "actionbar";

  /** How long the client keeps an action bar up on its own, in milliseconds. */
  private static final long ACTION_BAR_LIFETIME = 3_000L;

  /** Vanilla's default title timings, used until the backend sends its own. */
  private static final long DEFAULT_TITLE_LIFETIME = (10 + 70 + 20) * 50L;

  private final ProxyPulse plugin;
  private final Player player;
  private final MinecraftConnection connection;
  private final PacketIds packetIds;
  private final Map<String, TrackedElement> tracked = new ConcurrentHashMap<>();

  private volatile long titleLifetime = DEFAULT_TITLE_LIFETIME;

  public PlayerSession(final ProxyPulse plugin, final Player player,
      final MinecraftConnection connection) {
    this.plugin = plugin;
    this.player = player;
    this.connection = connection;
    this.packetIds = PacketIds.forVersion(player.getProtocolVersion());
  }

  public Player player() {
    return player;
  }

  public int trackedElements() {
    return tracked.size();
  }

  /** Drops everything being tracked, used when the player changes server. */
  public void clear() {
    tracked.clear();
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
    final ProtocolVersion version = player.getProtocolVersion();

    if (packetId == packetIds.headerAndFooter()) {
      final Component header = ComponentHolder.read(buffer, version).getComponent();
      final Component footer = ComponentHolder.read(buffer, version).getComponent();
      buffer.readerIndex(start);
      return rewriteHeaderAndFooter(buffer, header, footer, version);
    }
    if (packetId == packetIds.title()) {
      return rewriteSingleComponent(buffer, start, version, KEY_TITLE,
          GenericTitlePacket.ActionType.SET_TITLE, titleLifetime);
    }
    if (packetId == packetIds.subtitle()) {
      return rewriteSingleComponent(buffer, start, version, KEY_SUBTITLE,
          GenericTitlePacket.ActionType.SET_SUBTITLE, titleLifetime);
    }
    if (packetId == packetIds.actionBar()) {
      return rewriteSingleComponent(buffer, start, version, KEY_ACTION_BAR,
          GenericTitlePacket.ActionType.SET_ACTION_BAR, ACTION_BAR_LIFETIME);
    }
    if (packetId == packetIds.titleTimes()) {
      // Not rewritten, only read: it tells us how long the title the backend is about to send is
      // meant to stay up, which is exactly how long it is worth animating.
      readTitleTimes(buffer);
      buffer.readerIndex(start);
      return buffer;
    }

    buffer.readerIndex(start);
    return buffer;
  }

  private Object rewriteHeaderAndFooter(final ByteBuf buffer, final Component header,
      final Component footer, final ProtocolVersion version) {
    if (!PlaceholderEngine.mayContainPlaceholders(header)
        && !PlaceholderEngine.mayContainPlaceholders(footer)) {
      // The backend has replaced the animated header with a static one; it must stay static.
      tracked.remove(KEY_TABLIST);
      return buffer;
    }

    final long now = System.currentTimeMillis();
    final List<Component> rendered = List.of(render(header, now), render(footer, now));
    tracked.put(KEY_TABLIST, TrackedElement.of(List.of(header, footer), rendered,
        parts -> HeaderAndFooterPacket.create(parts.get(0), parts.get(1), version), 0L));

    buffer.release();
    debug("rewriting the tab list header and footer");
    return HeaderAndFooterPacket.create(rendered.get(0), rendered.get(1), version);
  }

  /**
   * Handles the packets that carry a single piece of text: titles, subtitles and the action bar.
   *
   * <p>These are the ones the client forgets on its own after a while. Re-sending one forever would
   * pin it to the screen long after the backend stopped caring about it, so each is refreshed only
   * for as long as the client would have kept showing it anyway.
   */
  private Object rewriteSingleComponent(final ByteBuf buffer, final int start,
      final ProtocolVersion version, final String key,
      final GenericTitlePacket.ActionType action, final long lifetime) {
    final Component text = ComponentHolder.read(buffer, version).getComponent();
    buffer.readerIndex(start);

    if (!PlaceholderEngine.mayContainPlaceholders(text)) {
      tracked.remove(key);
      return buffer;
    }

    final long now = System.currentTimeMillis();
    final Component rendered = render(text, now);
    tracked.put(key, TrackedElement.of(List.of(text), List.of(rendered),
        parts -> titlePacket(action, parts.get(0), version), now + lifetime));

    buffer.release();
    debug("rewriting " + key);
    return titlePacket(action, rendered, version);
  }

  /** Reads fade in, stay and fade out, all in ticks, and turns them into a lifetime. */
  private void readTitleTimes(final ByteBuf buffer) {
    if (buffer.readableBytes() < Integer.BYTES * 3) {
      return;
    }
    final long ticks = (long) buffer.readInt() + buffer.readInt() + buffer.readInt();
    this.titleLifetime = Math.max(0L, ticks) * 50L;
  }

  private static Object titlePacket(final GenericTitlePacket.ActionType action,
      final Component text, final ProtocolVersion version) {
    final GenericTitlePacket packet =
        GenericTitlePacket.constructTitlePacket(action, version);
    packet.setComponent(new ComponentHolder(version, text));
    return packet;
  }

  @Override
  public void refresh(final long timestampMillis) {
    if (!connection.getChannel().isActive()) {
      return;
    }
    tracked.forEach((key, element) -> {
      if (element.hasExpired(timestampMillis)) {
        tracked.remove(key);
        return;
      }
      final List<Component> rendered = element.originals().stream()
          .map(original -> render(original, timestampMillis))
          .toList();
      // Re-sending identical text every tick would be pure bandwidth: only a real change goes out.
      if (rendered.equals(element.lastSent())) {
        return;
      }
      tracked.put(key, element.withLastSent(rendered));
      connection.write(element.packetFactory().apply(rendered));
    });
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
