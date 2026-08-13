package dev.adriel.proxypulse;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.adriel.proxypulse.animation.AnimationRegistry;
import dev.adriel.proxypulse.animation.AnimationTicker;
import dev.adriel.proxypulse.config.ConfigManager;
import dev.adriel.proxypulse.config.PulseConfig;
import dev.adriel.proxypulse.placeholder.NetworkPlaceholders;
import dev.adriel.proxypulse.placeholder.PlaceholderEngine;
import dev.adriel.proxypulse.session.SessionManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;

/**
 * Entry point of ProxyPulse.
 *
 * <p>Packet rewriting is done natively against the proxy's own packet classes, so components stay
 * in the Adventure version the proxy itself ships and no protocol knowledge is duplicated here.
 */
@Plugin(
    id = "proxypulse",
    name = "ProxyPulse",
    version = "1.0.0",
    description = "Proxy-side animated placeholders and a global tab list for the whole network.",
    url = "https://github.com/adrielorihuela/ProxyPulse",
    authors = {"adrielorihuela"})
public final class ProxyPulse {

  private final ProxyServer proxy;
  private final Logger logger;
  private final ConfigManager configManager;
  private final AnimationRegistry animations = new AnimationRegistry();
  private final PlaceholderEngine placeholders = new PlaceholderEngine(animations);
  private final AnimationTicker ticker;
  private final SessionManager sessions;

  private volatile PulseConfig config = defaults();

  @Inject
  public ProxyPulse(final ProxyServer proxy, final Logger logger,
      final @DataDirectory Path dataDirectory) {
    this.proxy = proxy;
    this.logger = logger;
    this.configManager = new ConfigManager(dataDirectory);
    this.ticker = new AnimationTicker(throwable ->
        logger.warn("An element failed to refresh.", throwable));
    this.sessions = new SessionManager(this, ticker);
  }

  @Subscribe
  public void onProxyInitialize(final ProxyInitializeEvent event) {
    logger.info("Starting on {} {}", proxy.getVersion().getName(),
        proxy.getVersion().getVersion());

    new NetworkPlaceholders().registerAll(placeholders, proxy);
    reload();

    proxy.getEventManager().register(this, sessions);
    ticker.start();
  }

  @Subscribe
  public void onProxyShutdown(final ProxyShutdownEvent event) {
    ticker.stop();
    sessions.detachAll();
  }

  /**
   * Re-reads every file from disk. Returns false and keeps the previous state if anything is
   * unreadable, so a typo in the config never leaves the network without animations.
   */
  public boolean reload() {
    try {
      configManager.saveDefaults();
      this.config = configManager.loadConfig();

      final List<String> problems = animations.load(configManager.loadAnimations());
      problems.forEach(problem -> logger.warn("Skipped an animation: {}", problem));

      logger.info("Loaded {} animation(s); global tab list is {}.", animations.size(),
          config.globalTablist().enabled() ? "enabled" : "disabled");
      return problems.isEmpty();
    } catch (final IOException | RuntimeException exception) {
      logger.error("Could not load the configuration.", exception);
      return false;
    }
  }

  public PulseConfig config() {
    return config;
  }

  public ProxyServer proxy() {
    return proxy;
  }

  public Logger logger() {
    return logger;
  }

  public ConfigManager configManager() {
    return configManager;
  }

  public AnimationRegistry animations() {
    return animations;
  }

  public PlaceholderEngine placeholders() {
    return placeholders;
  }

  public SessionManager sessions() {
    return sessions;
  }

  private static PulseConfig defaults() {
    return new PulseConfig(500L, java.util.Map.of(),
        new PulseConfig.GlobalTablist(false, java.util.Set.of(), true, "%player%",
            java.util.List.of(), java.util.List.of(), java.util.List.of()),
        false);
  }
}
