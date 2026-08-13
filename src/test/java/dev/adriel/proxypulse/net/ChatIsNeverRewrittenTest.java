package dev.adriel.proxypulse.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.network.ProtocolVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one rule ProxyPulse must never break: a player cannot type an animation into chat and have
 * the proxy render it for everyone.
 *
 * <p>The plugin only ever acts on an allow-list of packet ids, so chat is excluded by construction
 * -- but "by construction" is exactly the kind of guarantee that quietly stops holding when someone
 * adds a packet later. These tests are here to fail loudly on that day.
 */
class ChatIsNeverRewrittenTest {

  @Test
  @DisplayName("no chat packet id is ever one of the ids the plugin rewrites")
  void chatIdsNeverOverlapWithRewrittenIds() {
    for (final ProtocolVersion version : ProtocolVersion.SUPPORTED_VERSIONS) {
      final PacketIds ids = PacketIds.forVersion(version);
      for (final int chatId : ids.chatIds()) {
        assertFalse(ids.matchesAny(chatId),
            () -> "chat packet " + chatId + " would be rewritten on " + version);
      }
    }
  }

  @Test
  @DisplayName("no chat packet id collides with a scoreboard id either")
  void chatIdsNeverOverlapWithScoreboardIds() {
    for (final ProtocolVersion version : ProtocolVersion.SUPPORTED_VERSIONS) {
      final PacketIds ids = PacketIds.forVersion(version);
      final ScoreboardIds scoreboard = ScoreboardIds.forVersion(version);
      if (!scoreboard.supported()) {
        continue;
      }
      for (final int chatId : ids.chatIds()) {
        assertFalse(chatId == scoreboard.setObjective() || chatId == scoreboard.setPlayerTeam(),
            () -> "chat packet " + chatId + " looks like a scoreboard packet on " + version);
      }
    }
  }

  @Test
  @DisplayName("chat packets really are recognised, so the guard is not vacuous")
  void chatIdsAreActuallyFound() {
    final PacketIds ids = PacketIds.forVersion(ProtocolVersion.MINECRAFT_1_21_5);
    assertFalse(ids.chatIds().isEmpty(), "no chat packet was recognised at all");
    ids.chatIds().forEach(chatId -> assertTrue(ids.isChat(chatId)));
  }
}
