package dev.adriel.proxypulse.placeholder;

import dev.adriel.proxypulse.animation.Animation;
import dev.adriel.proxypulse.animation.AnimationRegistry;
import dev.adriel.proxypulse.text.TextParser;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Replaces {@code %placeholder%} occurrences inside the text the backend servers send.
 *
 * <p>Replacement happens on the component tree, not on a flattened string. That matters: the text
 * arriving from a backend already carries colours, hover events and click events, and flattening it
 * to replace one word would throw all of that away. Adventure's own replacement walks the tree and
 * swaps only the matching literal, leaving the rest of the structure exactly as the backend meant
 * it.
 */
public final class PlaceholderEngine {

  /** Matches {@code %key%} and {@code %key:argument%}. */
  public static final Pattern PATTERN =
      Pattern.compile("%([a-zA-Z0-9_]+)(?::([^%\\s]+))?%");

  /** An animation frame may itself use placeholders; this caps how deep that can nest. */
  private static final int MAX_DEPTH = 3;

  private final Map<String, Placeholder> placeholders = new ConcurrentHashMap<>();
  private final AnimationRegistry animations;

  public PlaceholderEngine(final AnimationRegistry animations) {
    this.animations = animations;
  }

  public void register(final String key, final Placeholder placeholder) {
    placeholders.put(key.toLowerCase(Locale.ROOT), placeholder);
  }

  public java.util.Set<String> registeredKeys() {
    return java.util.Set.copyOf(placeholders.keySet());
  }

  /** Cheap test used to decide whether a packet is worth tracking at all. */
  public static boolean mayContainPlaceholders(final Component component) {
    return mayContainPlaceholders(PlainTextComponentSerializer.plainText().serialize(component));
  }

  public static boolean mayContainPlaceholders(final String text) {
    return text.indexOf('%') >= 0 && PATTERN.matcher(text).find();
  }

  /** Resolves every placeholder in a component, keeping the surrounding styling intact. */
  public Component render(final Component component, final PulseContext context) {
    return render(component, context, 0);
  }

  /** Parses a configured line and resolves its placeholders. */
  public Component renderLine(final String line, final PulseContext context) {
    return render(TextParser.parse(line), context, 0);
  }

  private Component render(final Component component, final PulseContext context, final int depth) {
    if (depth >= MAX_DEPTH) {
      return component;
    }
    return component.replaceText(TextReplacementConfig.builder()
        .match(PATTERN)
        .replacement((match, builder) -> replacement(match, context, depth))
        .build());
  }

  private Component replacement(final MatchResult match, final PulseContext context,
      final int depth) {
    final String key = match.group(1).toLowerCase(Locale.ROOT);
    final String argument = match.group(2);

    if ("animation".equals(key)) {
      final Optional<Animation> animation = argument == null
          ? Optional.empty()
          : animations.find(argument);
      // An unknown animation is left on screen as written: silently blanking it would send admins
      // hunting through packet captures for text that simply is not defined yet.
      return animation
          .map(found -> render(TextParser.parse(found.frameAt(context.timestampMillis())),
              context, depth + 1))
          .orElseGet(() -> Component.text(match.group()));
    }

    final Placeholder placeholder = placeholders.get(key);
    if (placeholder == null) {
      return Component.text(match.group());
    }
    final String value = placeholder.resolve(argument, context);
    return value == null
        ? Component.text(match.group())
        : render(TextParser.parse(value), context, depth + 1);
  }
}
