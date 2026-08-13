package dev.adriel.proxypulse;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
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
  private final Path dataDirectory;

  @Inject
  public ProxyPulse(final ProxyServer proxy, final Logger logger,
      final @DataDirectory Path dataDirectory) {
    this.proxy = proxy;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  @Subscribe
  public void onProxyInitialize(final ProxyInitializeEvent event) {
    logger.info("ProxyPulse starting on {} {} (data directory: {})",
        proxy.getVersion().getName(), proxy.getVersion().getVersion(), dataDirectory);
  }
}
