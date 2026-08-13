package dev.adriel.proxypulse.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Typed, immutable view of {@code config.yml}.
 *
 * <p>Reloading builds a brand new instance rather than mutating this one, so a reload can never be
 * observed half applied by a packet being rewritten on another thread.
 */
public record PulseConfig(
    long refreshInterval,
    Map<String, String> serverAliases,
    GlobalTablist globalTablist,
    boolean debug) {

  /** Settings for the network wide tab list. */
  public record GlobalTablist(
      boolean enabled,
      Set<String> disabledServers,
      boolean suppressBackendTablist,
      String nameFormat,
      List<String> sortBy,
      List<String> header,
      List<String> footer) {

    /** Whether the global list should be shown to players on the given server. */
    public boolean appliesTo(final String serverName) {
      return enabled && !disabledServers.contains(serverName.toLowerCase(java.util.Locale.ROOT));
    }
  }

  /** Reads a configuration out of the given root node, falling back to the documented defaults. */
  public static PulseConfig load(final ConfigurationNode root) throws SerializationException {
    final ConfigurationNode tablist = root.node("global-tablist");
    return new PulseConfig(
        Math.max(50L, root.node("refresh-interval").getLong(500L)),
        Map.copyOf(readStringMap(root.node("server-aliases"))),
        new GlobalTablist(
            tablist.node("enabled").getBoolean(false),
            lowerCased(readStringList(tablist.node("disabled-servers"))),
            tablist.node("suppress-backend-tablist").getBoolean(true),
            tablist.node("name-format").getString("%player%"),
            List.copyOf(readStringList(tablist.node("sort-by"))),
            List.copyOf(readStringList(tablist.node("header"))),
            List.copyOf(readStringList(tablist.node("footer")))),
        root.node("debug").getBoolean(false));
  }

  private static List<String> readStringList(final ConfigurationNode node)
      throws SerializationException {
    final List<String> values = node.getList(String.class);
    return values == null ? List.of() : values;
  }

  private static Map<String, String> readStringMap(final ConfigurationNode node) {
    final Map<Object, ? extends ConfigurationNode> children = node.childrenMap();
    return children.entrySet().stream().collect(java.util.stream.Collectors.toMap(
        entry -> String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT),
        entry -> entry.getValue().getString("")));
  }

  private static Set<String> lowerCased(final List<String> values) {
    return values.stream()
        .map(value -> value.toLowerCase(java.util.Locale.ROOT))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
