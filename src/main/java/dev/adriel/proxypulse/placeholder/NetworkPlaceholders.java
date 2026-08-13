package dev.adriel.proxypulse.placeholder;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * The placeholders only the proxy is in a position to answer.
 *
 * <p>Anything a backend already exposes through PlaceholderAPI -- TPS, world, coordinates, economy
 * -- is deliberately absent. Duplicating those here would mean answering them with worse
 * information than the server that actually owns them. What is here is the opposite case: figures
 * that no single backend can know, because they are about the network as a whole or about the
 * connection between the player and the proxy.
 */
public final class NetworkPlaceholders {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final long startedAt = System.currentTimeMillis();

  /** Registers every built-in placeholder on the given engine. */
  public void registerAll(final PlaceholderEngine engine, final ProxyServer proxy) {
    // -- The network as a whole -------------------------------------------------------------
    engine.register("network_online", (argument, context) ->
        String.valueOf(proxy.getPlayerCount()));
    engine.register("network_max", (argument, context) ->
        String.valueOf(proxy.getConfiguration().getShowMaxPlayers()));
    engine.register("server_count", (argument, context) ->
        String.valueOf(proxy.getAllServers().size()));
    engine.register("online_ratio", (argument, context) ->
        proxy.getPlayerCount() + "/" + proxy.getConfiguration().getShowMaxPlayers());

    // -- A specific backend, or the one the player is on ------------------------------------
    engine.register("server_online", (argument, context) -> {
      final Optional<RegisteredServer> server = argument == null
          ? currentServer(context.subject())
          : proxy.getServer(argument);
      return server.map(found -> String.valueOf(found.getPlayersConnected().size())).orElse("0");
    });
    engine.register("server_status", (argument, context) -> {
      final String name = argument == null
          ? currentServer(context.subject()).map(s -> s.getServerInfo().getName()).orElse(null)
          : argument;
      if (name == null) {
        return "unknown";
      }
      return proxy.getServer(name).isPresent() ? "online" : "unknown";
    });
    engine.register("server", (argument, context) ->
        currentServer(context.subject()).map(s -> s.getServerInfo().getName()).orElse("none"));
    engine.register("server_alias", (argument, context) -> alias(context, argument));
    // The row a global tab list draws is about someone else, which is exactly what subject means.
    engine.register("player_server", (argument, context) ->
        currentServer(context.subject()).map(s -> s.getServerInfo().getName()).orElse("none"));

    // -- The player ---------------------------------------------------------------------------
    engine.register("player", (argument, context) -> context.subject().getUsername());
    engine.register("player_uuid", (argument, context) ->
        context.subject().getUniqueId().toString());
    engine.register("ping", (argument, context) -> String.valueOf(context.subject().getPing()));
    engine.register("ping_colored", (argument, context) -> colouredPing(context.subject()));
    engine.register("client_version", (argument, context) ->
        context.subject().getProtocolVersion().getVersionIntroducedIn());
    engine.register("client_brand", (argument, context) -> {
      final String brand = context.subject().getClientBrand();
      return brand == null ? "unknown" : brand;
    });
    engine.register("locale", (argument, context) -> {
      final Locale locale = context.subject().getEffectiveLocale();
      return locale == null ? "unknown" : locale.toLanguageTag();
    });

    // -- The proxy itself ---------------------------------------------------------------------
    engine.register("proxy_version", (argument, context) -> proxy.getVersion().getVersion());
    engine.register("proxy_uptime", (argument, context) -> uptime());
    engine.register("date", (argument, context) -> LocalDateTime.now().format(DATE));
    engine.register("time", (argument, context) -> LocalDateTime.now().format(TIME));
  }

  private static Optional<RegisteredServer> currentServer(final Player player) {
    return player.getCurrentServer().map(connection -> connection.getServer());
  }

  private static String alias(final PulseContext context, final String argument) {
    final String name = argument == null
        ? currentServer(context.subject()).map(s -> s.getServerInfo().getName()).orElse(null)
        : argument;
    if (name == null) {
      return "none";
    }
    return context.config().serverAliases()
        .getOrDefault(name.toLowerCase(Locale.ROOT), name);
  }

  private static String colouredPing(final Player player) {
    final long ping = player.getPing();
    final String colour = ping < 0 ? "&7" : ping < 100 ? "&a" : ping < 250 ? "&e" : "&c";
    return colour + (ping < 0 ? "?" : String.valueOf(ping));
  }

  private String uptime() {
    final Duration uptime = Duration.ofMillis(System.currentTimeMillis() - startedAt);
    return String.format("%dd %dh %dm", uptime.toDays(), uptime.toHoursPart(),
        uptime.toMinutesPart());
  }
}
