package dev.adriel.proxypulse.net;

import com.velocitypowered.api.network.ProtocolVersion;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Packet ids for the two scoreboard packets ProxyPulse rewrites.
 *
 * <p>Unlike every other packet the plugin touches, these two are not in the proxy's registry: the
 * proxy has no reason to decode a scoreboard, so it forwards them untouched and never learns their
 * ids. That leaves no choice but to keep a table, which is why it is the only hand-maintained
 * mapping in the project and why each entry is written against a published protocol listing rather
 * than guessed.
 *
 * <p>A client on a version that is not in the table keeps its scoreboard exactly as the backend
 * sent it; everything else ProxyPulse does still works for them.
 *
 * @param setObjective id of the packet carrying an objective's title
 * @param setPlayerTeam id of the packet carrying team prefixes and suffixes, which is where
 *     sidebar lines actually live
 */
public record ScoreboardIds(int setObjective, int setPlayerTeam) {

  public static final ScoreboardIds UNSUPPORTED = new ScoreboardIds(-1, -1);

  /** Protocol number of the first version in each range, mapped to the ids that range uses. */
  private static final Map<Integer, ScoreboardIds> BY_PROTOCOL = new LinkedHashMap<>();

  static {
    BY_PROTOCOL.put(764, new ScoreboardIds(0x5A, 0x5C)); // 1.20.2
    BY_PROTOCOL.put(765, new ScoreboardIds(0x5C, 0x5E)); // 1.20.3 - 1.20.4
    BY_PROTOCOL.put(766, new ScoreboardIds(0x5E, 0x60)); // 1.20.5 - 1.21.1
    BY_PROTOCOL.put(768, new ScoreboardIds(0x64, 0x67)); // 1.21.2 - 1.21.4
    BY_PROTOCOL.put(770, new ScoreboardIds(0x63, 0x66)); // 1.21.5 - 1.21.8
    BY_PROTOCOL.put(773, new ScoreboardIds(0x68, 0x6B)); // 1.21.9 - 1.21.11
    BY_PROTOCOL.put(775, new ScoreboardIds(0x6A, 0x6D)); // 26.1
  }

  public static ScoreboardIds forVersion(final ProtocolVersion version) {
    final int protocol = version.getProtocol();
    ScoreboardIds match = UNSUPPORTED;
    for (final Map.Entry<Integer, ScoreboardIds> entry : BY_PROTOCOL.entrySet()) {
      if (protocol >= entry.getKey()) {
        match = entry.getValue();
      }
    }
    // Anything newer than the last known entry is left alone rather than rewritten with ids that
    // may since have moved: a wrong id here would corrupt the connection, not just the text.
    final int newest = BY_PROTOCOL.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    return protocol > newest + 1 ? UNSUPPORTED : match;
  }

  public boolean supported() {
    return setObjective >= 0;
  }
}
