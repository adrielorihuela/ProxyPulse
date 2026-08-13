package dev.adriel.proxypulse.tablist;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.RemovePlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.UpsertPlayerInfoPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import dev.adriel.proxypulse.ProxyPulse;
import dev.adriel.proxypulse.config.PulseConfig;
import dev.adriel.proxypulse.placeholder.PulseContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/**
 * One player's view of the network wide tab list.
 *
 * <p>The list is rebuilt from the proxy's own player list every tick and compared against what this
 * player was last sent, so only real changes go out. Building it from scratch each time is both
 * simpler and more correct than trying to keep an incremental model in sync with joins, quits and
 * server switches happening across the whole network.
 *
 * <p>The list order field it uses to sort rows only exists from 1.21.2 onwards. Older clients get
 * the same names and the same header, just in the order the proxy happens to list them.
 */
public final class GlobalTabView {

  /** The tab list packet used here only exists from 1.19.3 onwards. */
  private static final ProtocolVersion MINIMUM_VERSION = ProtocolVersion.MINECRAFT_1_19_3;

  /** Sorting rows by an explicit index is only possible from 1.21.2 onwards. */
  private static final ProtocolVersion LIST_ORDER_VERSION = ProtocolVersion.MINECRAFT_1_21_2;

  private final ProxyPulse plugin;
  private final Player viewer;

  private final Map<UUID, Row> lastSent = new LinkedHashMap<>();
  private Component lastHeader;
  private Component lastFooter;

  public GlobalTabView(final ProxyPulse plugin, final Player viewer) {
    this.plugin = plugin;
    this.viewer = viewer;
  }

  private record Row(String name, Component displayName, int latency, int order) {
  }

  /** Whether this player should be seeing the global list right now. */
  public boolean isActive() {
    if (viewer.getProtocolVersion().lessThan(MINIMUM_VERSION)) {
      return false;
    }
    final PulseConfig.GlobalTablist settings = plugin.config().globalTablist();
    return viewer.getCurrentServer()
        .map(connection -> settings.appliesTo(connection.getServerInfo().getName()))
        .orElse(false);
  }

  /** True while this view still has entries on the client that need taking back. */
  public boolean hasEntries() {
    return !lastSent.isEmpty();
  }

  /**
   * Brings the client's list in line with the network, sending whatever it takes and nothing more.
   *
   * @param send how to put a packet on the wire
   */
  public void refresh(final long timestampMillis, final Consumer<Object> send) {
    final ProtocolVersion version = viewer.getProtocolVersion();
    final Map<UUID, Row> current = buildRows(timestampMillis);

    final List<UUID> gone = lastSent.keySet().stream()
        .filter(uuid -> !current.containsKey(uuid))
        .toList();
    if (!gone.isEmpty()) {
      send.accept(new RemovePlayerInfoPacket(gone));
      gone.forEach(lastSent::remove);
    }

    current.forEach((uuid, row) -> {
      final Row previous = lastSent.get(uuid);
      if (row.equals(previous)) {
        return;
      }
      send.accept(entryPacket(uuid, row, previous == null, version));
      lastSent.put(uuid, row);
    });

    refreshHeaderAndFooter(timestampMillis, version, send);
  }

  /** Takes back every row this view added, leaving the backend's own list to take over. */
  public void reset(final Consumer<Object> send) {
    if (lastSent.isEmpty()) {
      return;
    }
    send.accept(new RemovePlayerInfoPacket(List.copyOf(lastSent.keySet())));
    lastSent.clear();
    lastHeader = null;
    lastFooter = null;
  }

  private Map<UUID, Row> buildRows(final long timestampMillis) {
    final PulseConfig.GlobalTablist settings = plugin.config().globalTablist();
    final List<Player> players = new ArrayList<>(plugin.proxy().getAllPlayers());
    players.sort(comparator(settings.sortBy()));

    final Map<UUID, Row> rows = new LinkedHashMap<>();
    for (int index = 0; index < players.size(); index++) {
      final Player subject = players.get(index);
      final PulseContext context =
          PulseContext.of(plugin.proxy(), plugin.config(), viewer, timestampMillis)
              .about(subject);
      rows.put(subject.getUniqueId(), new Row(
          subject.getUsername(),
          plugin.placeholders().renderLine(settings.nameFormat(), context),
          (int) Math.max(0L, subject.getPing()),
          // Rows are ordered highest first by the client, so the index is flipped.
          players.size() - index));
    }
    return rows;
  }

  /** Builds the comparator described by the configured sort keys, in the order they are listed. */
  private static Comparator<Player> comparator(final List<String> sortBy) {
    Comparator<Player> comparator = null;
    for (final String key : sortBy) {
      final Comparator<Player> next = switch (key.toLowerCase(Locale.ROOT)) {
        case "server" -> Comparator.comparing(player -> player.getCurrentServer()
            .map(connection -> connection.getServerInfo().getName()).orElse(""));
        case "ping" -> Comparator.comparingLong(Player::getPing);
        case "name" -> Comparator.comparing(player ->
            player.getUsername().toLowerCase(Locale.ROOT));
        default -> null;
      };
      if (next != null) {
        comparator = comparator == null ? next : comparator.thenComparing(next);
      }
    }
    final Comparator<Player> byName =
        Comparator.comparing(player -> player.getUsername().toLowerCase(Locale.ROOT));
    return comparator == null ? byName : comparator.thenComparing(byName);
  }

  private Object entryPacket(final UUID uuid, final Row row, final boolean isNew,
      final ProtocolVersion version) {
    final UpsertPlayerInfoPacket packet = new UpsertPlayerInfoPacket();
    final UpsertPlayerInfoPacket.Entry entry = new UpsertPlayerInfoPacket.Entry(uuid);

    if (isNew) {
      // The profile carries the skin, which is what puts a face next to the name.
      packet.addAction(UpsertPlayerInfoPacket.Action.ADD_PLAYER);
      packet.addAction(UpsertPlayerInfoPacket.Action.UPDATE_LISTED);
      entry.setProfile(profileOf(uuid, row.name()));
      entry.setListed(true);
    }
    packet.addAction(UpsertPlayerInfoPacket.Action.UPDATE_LATENCY);
    packet.addAction(UpsertPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME);
    entry.setLatency(row.latency());
    entry.setDisplayName(new ComponentHolder(version, row.displayName()));

    if (version.noLessThan(LIST_ORDER_VERSION)) {
      packet.addAction(UpsertPlayerInfoPacket.Action.UPDATE_LIST_ORDER);
      entry.setListOrder(row.order());
    }

    packet.addEntry(entry);
    return packet;
  }

  private com.velocitypowered.api.util.GameProfile profileOf(final UUID uuid, final String name) {
    return plugin.proxy().getPlayer(uuid)
        .map(Player::getGameProfile)
        .orElseGet(() -> new com.velocitypowered.api.util.GameProfile(uuid, name, List.of()));
  }

  private void refreshHeaderAndFooter(final long timestampMillis, final ProtocolVersion version,
      final Consumer<Object> send) {
    final PulseConfig.GlobalTablist settings = plugin.config().globalTablist();
    if (settings.header().isEmpty() && settings.footer().isEmpty()) {
      return;
    }
    final PulseContext context =
        PulseContext.of(plugin.proxy(), plugin.config(), viewer, timestampMillis);
    final Component header = joinLines(settings.header(), context);
    final Component footer = joinLines(settings.footer(), context);

    if (header.equals(lastHeader) && footer.equals(lastFooter)) {
      return;
    }
    lastHeader = header;
    lastFooter = footer;
    send.accept(HeaderAndFooterPacket.create(header, footer, version));
  }

  private Component joinLines(final List<String> lines, final PulseContext context) {
    Component result = Component.empty();
    for (int index = 0; index < lines.size(); index++) {
      if (index > 0) {
        result = result.append(Component.newline());
      }
      result = result.append(plugin.placeholders().renderLine(lines.get(index), context));
    }
    return result;
  }
}
