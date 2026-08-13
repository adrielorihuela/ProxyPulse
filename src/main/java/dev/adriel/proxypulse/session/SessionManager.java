package dev.adriel.proxypulse.session;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.network.Connections;
import dev.adriel.proxypulse.ProxyPulse;
import dev.adriel.proxypulse.animation.AnimationTicker;
import dev.adriel.proxypulse.net.PulseOutboundHandler;
import io.netty.channel.ChannelPipeline;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attaches a session to every player as they log in and takes it away when they leave.
 *
 * <p>Failing to attach is not fatal: the player simply sees whatever the backend sent, which is
 * exactly what they would see without the plugin installed. That matters on a proxy, where one
 * unexpected pipeline layout should not be able to stop people from playing.
 */
public final class SessionManager {

  private final ProxyPulse plugin;
  private final AnimationTicker ticker;
  private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

  public SessionManager(final ProxyPulse plugin, final AnimationTicker ticker) {
    this.plugin = plugin;
    this.ticker = ticker;
  }

  @Subscribe
  public void onPostLogin(final PostLoginEvent event) {
    attach(event.getPlayer());
  }

  @Subscribe
  public void onDisconnect(final DisconnectEvent event) {
    detach(event.getPlayer());
  }

  public PlayerSession session(final Player player) {
    return sessions.get(player.getUniqueId());
  }

  public int size() {
    return sessions.size();
  }

  /** Removes every session, used when the plugin reloads or the proxy shuts down. */
  public void detachAll() {
    sessions.keySet().forEach(uuid -> {
      final PlayerSession session = sessions.remove(uuid);
      if (session != null) {
        ticker.unsubscribe(session);
      }
    });
  }

  private void attach(final Player player) {
    if (!(player instanceof ConnectedPlayer connectedPlayer)) {
      plugin.logger().warn("Unrecognised player implementation {}; ProxyPulse will stay out of "
          + "the way for {}.", player.getClass().getName(), player.getUsername());
      return;
    }

    final MinecraftConnection connection = connectedPlayer.getConnection();
    final ChannelPipeline pipeline = connection.getChannel().pipeline();
    if (pipeline.get(Connections.MINECRAFT_ENCODER) == null) {
      plugin.logger().warn("No packet encoder on {}'s connection; skipping.",
          player.getUsername());
      return;
    }

    final PlayerSession session = new PlayerSession(plugin, player, connection);
    // Placed after the encoder in pipeline order, which for an outgoing packet means before it:
    // the packet is still an object or an untouched buffer when it reaches the session.
    pipeline.addAfter(Connections.MINECRAFT_ENCODER, PulseOutboundHandler.NAME,
        new PulseOutboundHandler(session));

    sessions.put(player.getUniqueId(), session);
    ticker.subscribe(session);
  }

  private void detach(final Player player) {
    final PlayerSession session = sessions.remove(player.getUniqueId());
    if (session != null) {
      ticker.unsubscribe(session);
    }
  }
}
