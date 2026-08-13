package dev.adriel.proxypulse.net;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.chat.PlayerChatCompletionPacket;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChatPacket;
import com.velocitypowered.proxy.protocol.packet.chat.legacy.LegacyChatPacket;
import com.velocitypowered.proxy.protocol.packet.title.GenericTitlePacket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Packet ids of the clientbound packets ProxyPulse has to recognise in raw form.
 *
 * <p>Some packets the proxy only ever encodes -- tab list header and footer, titles -- so when a
 * backend sends one it arrives as an undecoded buffer whose first field is the packet id. To know
 * which id that is for the client in question, the ids are read out of the proxy's own registry
 * rather than kept in a table here: the proxy already tracks them for every protocol version it
 * supports, and a hand-maintained copy would go stale the day a new Minecraft version renumbers
 * them.
 *
 * @param headerAndFooter id of the tab list header/footer packet, or -1 if this client's version
 *     does not have it
 * @param title id of the title text packet, or -1
 * @param subtitle id of the subtitle packet, or -1
 * @param actionBar id of the action bar packet, or -1
 * @param titleTimes id of the title timing packet, or -1
 * @param chatIds ids of every clientbound chat packet this client version has, so that they can be
 *     recognised and refused
 */
public record PacketIds(int headerAndFooter, int title, int subtitle, int actionBar,
                        int titleTimes, java.util.Set<Integer> chatIds) {

  private static final Map<ProtocolVersion, PacketIds> CACHE = new ConcurrentHashMap<>();

  public static PacketIds forVersion(final ProtocolVersion version) {
    return CACHE.computeIfAbsent(version, PacketIds::resolve);
  }

  private static PacketIds resolve(final ProtocolVersion version) {
    final StateRegistry.PacketRegistry.ProtocolRegistry registry =
        StateRegistry.PLAY.getProtocolRegistry(ProtocolUtils.Direction.CLIENTBOUND, version);

    return new PacketIds(
        idOf(registry, () -> HeaderAndFooterPacket.reset(version)),
        idOf(registry, () -> GenericTitlePacket.constructTitlePacket(
            GenericTitlePacket.ActionType.SET_TITLE, version)),
        idOf(registry, () -> GenericTitlePacket.constructTitlePacket(
            GenericTitlePacket.ActionType.SET_SUBTITLE, version)),
        idOf(registry, () -> GenericTitlePacket.constructTitlePacket(
            GenericTitlePacket.ActionType.SET_ACTION_BAR, version)),
        idOf(registry, () -> GenericTitlePacket.constructTitlePacket(
            GenericTitlePacket.ActionType.SET_TIMES, version)),
        chatIds(registry));
  }

  /** Older clients simply do not have some of these packets; -1 means "never match". */
  private static int idOf(final StateRegistry.PacketRegistry.ProtocolRegistry registry,
      final Supplier<MinecraftPacket> packet) {
    try {
      return registry.getPacketId(packet.get());
    } catch (final RuntimeException exception) {
      return -1;
    }
  }

  /**
   * Ids of the packets that carry chat.
   *
   * <p>Nothing here is ever rewritten. They are looked up for the opposite reason: so that a chat
   * packet can be recognised and refused outright. A player must not be able to type an animation
   * into chat and have the proxy render it, and that guarantee should not rest on the incidental
   * fact that no id happens to collide.
   */
  private static java.util.Set<Integer> chatIds(
      final StateRegistry.PacketRegistry.ProtocolRegistry registry) {
    return java.util.stream.Stream.of(
            idOf(registry, SystemChatPacket::new),
            idOf(registry, LegacyChatPacket::new),
            idOf(registry, PlayerChatCompletionPacket::new))
        .filter(id -> id >= 0)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  /** Whether this id belongs to a chat packet, which ProxyPulse never touches. */
  public boolean isChat(final int packetId) {
    return chatIds.contains(packetId);
  }

  public boolean matchesAny(final int packetId) {
    return packetId == headerAndFooter || packetId == title || packetId == subtitle
        || packetId == actionBar || packetId == titleTimes;
  }
}
