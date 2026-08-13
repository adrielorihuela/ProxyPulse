package dev.adriel.proxypulse.session;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.BossBarPacket;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.LegacyPlayerListItemPacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.title.GenericTitlePacket;
import dev.adriel.proxypulse.ProxyPulse;
import dev.adriel.proxypulse.animation.AnimationTicker;
import dev.adriel.proxypulse.net.PacketIds;
import dev.adriel.proxypulse.net.PulseOutboundHandler;
import dev.adriel.proxypulse.net.ScoreboardCodec;
import dev.adriel.proxypulse.net.ScoreboardIds;
import dev.adriel.proxypulse.placeholder.PlaceholderEngine;
import dev.adriel.proxypulse.placeholder.PulseContext;
import dev.adriel.proxypulse.tablist.GlobalTabView;
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
  private static final String KEY_BOSS_BAR = "bossbar:";
  private static final String KEY_TAB_ENTRY = "tabentry:";

  /** How long the client keeps an action bar up on its own, in milliseconds. */
  private static final long ACTION_BAR_LIFETIME = 3_000L;

  /** Vanilla's default title timings, used until the backend sends its own. */
  private static final long DEFAULT_TITLE_LIFETIME = (10 + 70 + 20) * 50L;

  private final ProxyPulse plugin;
  private final Player player;
  private final MinecraftConnection connection;
  private final PacketIds packetIds;
  private final ScoreboardIds scoreboardIds;
  private final Map<String, TrackedElement> tracked = new ConcurrentHashMap<>();
  private final GlobalTabView globalTab;

  private volatile long titleLifetime = DEFAULT_TITLE_LIFETIME;

  public PlayerSession(final ProxyPulse plugin, final Player player,
      final MinecraftConnection connection) {
    this.plugin = plugin;
    this.player = player;
    this.connection = connection;
    this.packetIds = PacketIds.forVersion(player.getProtocolVersion());
    this.scoreboardIds = ScoreboardIds.forVersion(player.getProtocolVersion());
    this.globalTab = new GlobalTabView(plugin, player);
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

  /**
   * Rewrites one outgoing message, returns it untouched when there is nothing to do, or returns
   * null to drop it entirely.
   */
  public Object rewrite(final Object message) {
    if (suppressesBackendTabList() && isTabListPacket(message)) {
      // The global list is the one on screen; letting the backend's rows through as well would put
      // two competing lists in front of the player.
      return null;
    }
    if (message instanceof ByteBuf buffer) {
      return rewriteRaw(buffer);
    }
    if (message instanceof BossBarPacket bossBar) {
      return rewriteBossBar(bossBar);
    }
    if (message instanceof UpsertPlayerInfoPacket playerInfo) {
      return rewritePlayerInfo(playerInfo);
    }
    if (message instanceof RemovePlayerInfoPacket removal) {
      removal.getProfilesToRemove().forEach(uuid -> tracked.remove(KEY_TAB_ENTRY + uuid));
    }
    return message;
  }

  /**
   * Boss bars are addressed by uuid, so an animated one is refreshed with an update-name packet
   * rather than by adding the bar again -- re-adding it would make the bar flash on the client.
   */
  private Object rewriteBossBar(final BossBarPacket packet) {
    final java.util.UUID uuid = packet.getUuid();
    if (packet.getAction() == BossBarPacket.REMOVE) {
      tracked.remove(KEY_BOSS_BAR + uuid);
      return packet;
    }
    if (packet.getAction() != BossBarPacket.ADD
        && packet.getAction() != BossBarPacket.UPDATE_NAME) {
      return packet;
    }

    final ComponentHolder holder = packet.getName();
    if (holder == null) {
      return packet;
    }
    final ProtocolVersion version = player.getProtocolVersion();
    final Component name = holder.getComponent();
    if (!PlaceholderEngine.mayContainPlaceholders(name)) {
      tracked.remove(KEY_BOSS_BAR + uuid);
      return packet;
    }

    final long now = System.currentTimeMillis();
    final Component rendered = render(name, now);
    tracked.put(KEY_BOSS_BAR + uuid, TrackedElement.of(List.of(name), List.of(rendered),
        parts -> bossBarNameUpdate(uuid, parts.get(0), version), 0L,
        animationsUsedBy(List.of(name))));

    packet.setName(new ComponentHolder(version, rendered));
    debug("rewriting boss bar " + uuid);
    return packet;
  }

  private static Object bossBarNameUpdate(final java.util.UUID uuid, final Component name,
      final ProtocolVersion version) {
    final BossBarPacket update = new BossBarPacket();
    update.setUuid(uuid);
    update.setAction(BossBarPacket.UPDATE_NAME);
    update.setName(new ComponentHolder(version, name));
    return update;
  }

  /** Rewrites the display names a backend puts on tab list rows. */
  private Object rewritePlayerInfo(final UpsertPlayerInfoPacket packet) {
    if (!packet.containsAction(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME)) {
      return packet;
    }
    final ProtocolVersion version = player.getProtocolVersion();
    final long now = System.currentTimeMillis();

    for (final UpsertPlayerInfoPacket.Entry entry : packet.getEntries()) {
      final ComponentHolder holder = entry.getDisplayName();
      final java.util.UUID uuid = entry.getProfileId();
      if (holder == null) {
        tracked.remove(KEY_TAB_ENTRY + uuid);
        continue;
      }
      final Component name = holder.getComponent();
      if (!PlaceholderEngine.mayContainPlaceholders(name)) {
        tracked.remove(KEY_TAB_ENTRY + uuid);
        continue;
      }

      final Component rendered = render(name, now);
      tracked.put(KEY_TAB_ENTRY + uuid, TrackedElement.of(List.of(name), List.of(rendered),
          parts -> displayNameUpdate(uuid, parts.get(0), version), 0L,
          animationsUsedBy(List.of(name))));
      entry.setDisplayName(new ComponentHolder(version, rendered));
      debug("rewriting the tab list name of " + uuid);
    }
    return packet;
  }

  private static Object displayNameUpdate(final java.util.UUID uuid, final Component name,
      final ProtocolVersion version) {
    final UpsertPlayerInfoPacket update =
        new UpsertPlayerInfoPacket(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME);
    final UpsertPlayerInfoPacket.Entry entry = new UpsertPlayerInfoPacket.Entry(uuid);
    entry.setDisplayName(new ComponentHolder(version, name));
    update.addEntry(entry);
    return update;
  }

  private boolean suppressesBackendTabList() {
    return plugin.config().globalTablist().suppressBackendTablist() && globalTab.isActive();
  }

  private boolean isTabListPacket(final Object message) {
    if (message instanceof UpsertPlayerInfoPacket || message instanceof RemovePlayerInfoPacket
        || message instanceof LegacyPlayerListItemPacket) {
      return true;
    }
    if (!(message instanceof ByteBuf buffer)) {
      return false;
    }
    final int start = buffer.readerIndex();
    try {
      return ProtocolUtils.readVarInt(buffer) == packetIds.headerAndFooter();
    } catch (final RuntimeException exception) {
      return false;
    } finally {
      buffer.readerIndex(start);
    }
  }

  private Object rewriteRaw(final ByteBuf buffer) {
    final int start = buffer.readerIndex();
    final int packetId = ProtocolUtils.readVarInt(buffer);
    final ProtocolVersion version = player.getProtocolVersion();

    if (packetIds.isChat(packetId)) {
      // Chat is off limits by design: a player must never be able to type %animation:logo% into
      // chat and have the proxy animate it for everyone.
      buffer.readerIndex(start);
      return buffer;
    }

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

    if (scoreboardIds.supported()
        && (packetId == scoreboardIds.setObjective() || packetId == scoreboardIds.setPlayerTeam())) {
      buffer.readerIndex(start);
      return rewriteScoreboard(buffer, packetId, version);
    }

    buffer.readerIndex(start);
    return buffer;
  }

  /**
   * Rewrites an objective title, or a team's display name, prefix and suffix.
   *
   * <p>Sidebar lines are team prefixes and suffixes in practice, which is why teams are worth the
   * trouble of a codec of their own.
   */
  private Object rewriteScoreboard(final ByteBuf buffer, final int packetId,
      final ProtocolVersion version) {
    final ScoreboardCodec.Parsed parsed = packetId == scoreboardIds.setObjective()
        ? ScoreboardCodec.parseObjective(buffer, version)
        : ScoreboardCodec.parseTeam(buffer, version);

    if (parsed == null) {
      return buffer;
    }
    if (parsed.components().stream().noneMatch(PlaceholderEngine::mayContainPlaceholders)) {
      tracked.remove(parsed.key());
      return buffer;
    }

    final long now = System.currentTimeMillis();
    final List<Component> rendered = parsed.components().stream()
        .map(original -> render(original, now))
        .toList();
    tracked.put(parsed.key(), TrackedElement.of(parsed.components(), rendered,
        parts -> parsed.rebuild(parts, version), 0L,
        animationsUsedBy(parsed.components())));

    buffer.release();
    debug("rewriting " + parsed.key());
    return parsed.rebuild(rendered, version);
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
        parts -> HeaderAndFooterPacket.create(parts.get(0), parts.get(1), version), 0L,
        animationsUsedBy(List.of(header, footer))));

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
        parts -> titlePacket(action, parts.get(0), version), now + lifetime,
        animationsUsedBy(List.of(text))));

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
    if (globalTab.isActive()) {
      globalTab.refresh(timestampMillis, this::send);
    } else if (globalTab.hasEntries()) {
      // The player has moved somewhere the global list does not apply; take back the rows it added
      // so the server's own list is the only one left.
      globalTab.reset(this::send);
    }
    final long refreshBucket = timestampMillis / plugin.config().refreshInterval();
    tracked.forEach((key, element) -> {
      if (element.hasExpired(timestampMillis)) {
        tracked.remove(key);
        return;
      }
      final long signature = element.signature(timestampMillis, refreshBucket);
      if (signature == element.lastSignature()) {
        // Nothing that feeds this text has moved since it was last rendered.
        return;
      }

      final List<Component> rendered = element.originals().stream()
          .map(original -> render(original, timestampMillis))
          .toList();
      tracked.put(key, element.rendered(rendered, signature));
      // Re-sending identical text would be pure bandwidth: only a real change goes out.
      if (rendered.equals(element.lastSent())) {
        return;
      }
      send(element.packetFactory().apply(rendered));
    });
  }

  /**
   * Sends a refreshed packet from below this session's own handler.
   *
   * <p>Writing it the ordinary way would send it back through the handler that produced it, where
   * the text -- already rendered, no placeholders left in it -- would look like a plain packet from
   * the backend and the element would immediately stop being tracked. Starting the write at this
   * handler's own position skips it and leaves the tracking alone.
   */
  private void send(final Object packet) {
    final io.netty.channel.ChannelHandlerContext context =
        connection.getChannel().pipeline().context(PulseOutboundHandler.NAME);
    if (context == null) {
      connection.write(packet);
      return;
    }
    context.writeAndFlush(packet);
  }

  /**
   * Works out which animations a piece of text uses, once, when it is first seen.
   *
   * <p>Knowing this up front is what lets a tick decide whether re-rendering is worth it at all.
   */
  private List<dev.adriel.proxypulse.animation.Animation> animationsUsedBy(
      final List<Component> components) {
    return components.stream()
        .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            ::serialize)
        .flatMap(text -> PlaceholderEngine.PATTERN.matcher(text).results())
        .filter(match -> "animation".equalsIgnoreCase(match.group(1)) && match.group(2) != null)
        .map(match -> plugin.animations().find(match.group(2)))
        .flatMap(java.util.Optional::stream)
        .distinct()
        .toList();
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
