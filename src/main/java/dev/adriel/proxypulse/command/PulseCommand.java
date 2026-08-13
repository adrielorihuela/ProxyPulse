package dev.adriel.proxypulse.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.adriel.proxypulse.ProxyPulse;
import dev.adriel.proxypulse.placeholder.PulseContext;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** The {@code /proxypulse} command: reloading, and enough visibility to debug a placeholder. */
public final class PulseCommand implements SimpleCommand {

  public static final String PERMISSION = "proxypulse.admin";

  private final ProxyPulse plugin;

  public PulseCommand(final ProxyPulse plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean hasPermission(final Invocation invocation) {
    return invocation.source().hasPermission(PERMISSION);
  }

  @Override
  public List<String> suggest(final Invocation invocation) {
    if (invocation.arguments().length <= 1) {
      return List.of("reload", "animations", "placeholders", "status", "preview");
    }
    return List.of();
  }

  @Override
  public void execute(final Invocation invocation) {
    final CommandSource source = invocation.source();
    final String[] arguments = invocation.arguments();
    final String subcommand = arguments.length == 0
        ? "help"
        : arguments[0].toLowerCase(Locale.ROOT);

    switch (subcommand) {
      case "reload" -> reload(source);
      case "animations" -> listAnimations(source);
      case "placeholders" -> listPlaceholders(source);
      case "status" -> status(source);
      case "preview" -> preview(source, arguments);
      default -> help(source);
    }
  }

  private void reload(final CommandSource source) {
    final boolean clean = plugin.reload();
    source.sendMessage(clean
        ? info("Reloaded " + plugin.animations().size() + " animation(s).")
        : warn("Reloaded with problems -- see the console for what was skipped."));
  }

  private void listAnimations(final CommandSource source) {
    if (plugin.animations().size() == 0) {
      source.sendMessage(warn("No animations are loaded."));
      return;
    }
    source.sendMessage(info("Animations:"));
    plugin.animations().all().values().forEach(animation -> source.sendMessage(
        Component.text("  %animation:" + animation.name() + "%", NamedTextColor.WHITE)
            .append(Component.text(
                "  " + animation.frames().size() + " frames, every "
                    + animation.changeInterval() + "ms", NamedTextColor.GRAY))));
  }

  private void listPlaceholders(final CommandSource source) {
    source.sendMessage(info("Placeholders:"));
    plugin.placeholders().registeredKeys().stream().sorted().forEach(key ->
        source.sendMessage(Component.text("  %" + key + "%", NamedTextColor.WHITE)));
  }

  private void status(final CommandSource source) {
    source.sendMessage(info("ProxyPulse status"));
    source.sendMessage(detail("animations", String.valueOf(plugin.animations().size())));
    source.sendMessage(detail("tracked sessions", String.valueOf(plugin.sessions().size())));
    source.sendMessage(detail("global tab list",
        plugin.config().globalTablist().enabled() ? "enabled" : "disabled"));
    source.sendMessage(detail("debug logging",
        plugin.config().debug() ? "on" : "off"));
  }

  /**
   * Renders a line the way a backend's text would be rendered, for the player running the command.
   *
   * <p>This is the quickest answer to "why is my placeholder not showing": if it renders here, the
   * plugin understands it and the problem is on the way the backend sends it.
   */
  private void preview(final CommandSource source, final String[] arguments) {
    if (!(source instanceof Player player)) {
      source.sendMessage(warn("Only a player can preview: the text is rendered for someone."));
      return;
    }
    if (arguments.length < 2) {
      source.sendMessage(warn("Usage: /proxypulse preview <text>"));
      return;
    }
    final String text = String.join(" ", java.util.Arrays.copyOfRange(arguments, 1,
        arguments.length));
    source.sendMessage(plugin.placeholders().renderLine(text,
        PulseContext.of(plugin.proxy(), plugin.config(), player, System.currentTimeMillis())));
  }

  private void help(final CommandSource source) {
    source.sendMessage(info("ProxyPulse commands"));
    source.sendMessage(detail("/proxypulse reload", "re-read config.yml and animations.yml"));
    source.sendMessage(detail("/proxypulse animations", "list every loaded animation"));
    source.sendMessage(detail("/proxypulse placeholders", "list every placeholder"));
    source.sendMessage(detail("/proxypulse status", "what the plugin is currently doing"));
    source.sendMessage(detail("/proxypulse preview <text>", "render a line as a backend would"));
  }

  private static Component info(final String message) {
    return Component.text("[ProxyPulse] ", NamedTextColor.AQUA)
        .append(Component.text(message, NamedTextColor.WHITE));
  }

  private static Component warn(final String message) {
    return Component.text("[ProxyPulse] ", NamedTextColor.AQUA)
        .append(Component.text(message, NamedTextColor.YELLOW));
  }

  private static Component detail(final String name, final String value) {
    return Component.text("  " + name + ": ", NamedTextColor.GRAY)
        .append(Component.text(value, NamedTextColor.WHITE));
  }

  /** Registers the command and its short alias. */
  public static void register(final ProxyPulse plugin) {
    final var manager = plugin.proxy().getCommandManager();
    manager.register(
        manager.metaBuilder("proxypulse").aliases("pulse", "pp").plugin(plugin).build(),
        new PulseCommand(plugin));
  }
}
