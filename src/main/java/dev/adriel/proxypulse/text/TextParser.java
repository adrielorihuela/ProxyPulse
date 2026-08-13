package dev.adriel.proxypulse.text;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Turns the text people write in the config files into components.
 *
 * <p>Server owners write both styles, often in the same file and sometimes in the same line: modern
 * MiniMessage tags for gradients, and the legacy ampersand codes muscle memory produces. Rather
 * than making them choose, legacy codes are translated into their MiniMessage equivalents first and
 * the whole string then goes through MiniMessage once.
 */
public final class TextParser {

  private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

  /** Legacy colour and style codes, ampersand or section sign, including {@code &#rrggbb}. */
  private static final Pattern LEGACY_CODE = Pattern.compile("[&§](#[0-9a-fA-F]{6}|[0-9a-fk-orA-FK-OR])");

  private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
      Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
      Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
      Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
      Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
      Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
      Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
      Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
      Map.entry('r', "reset"));

  /**
   * Parsed text, kept because the same handful of strings are parsed over and over.
   *
   * <p>An animation's frames are a small fixed set that gets parsed twenty times a second for every
   * player watching it, and parsing a gradient is by far the most expensive thing this plugin does
   * per tick. Components are immutable, so one parse can be shared by everyone.
   */
  private static final Map<String, Component> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

  /** Enough for any sane set of animation frames, and a hard stop on unbounded growth. */
  private static final int CACHE_LIMIT = 4096;

  private TextParser() {
  }

  /** Parses a line of configured text into a component. */
  public static Component parse(final String text) {
    final Component cached = CACHE.get(text);
    if (cached != null) {
      return cached;
    }
    final Component parsed = MINI_MESSAGE.deserialize(legacyToMiniMessage(text));
    if (CACHE.size() >= CACHE_LIMIT) {
      // Text this varied is not frame text; caching it would only grow without bound.
      return parsed;
    }
    CACHE.put(text, parsed);
    return parsed;
  }

  /** Drops the cache, so edited animations are re-read rather than served from memory. */
  public static void clearCache() {
    CACHE.clear();
  }

  /** Rewrites legacy colour codes as MiniMessage tags, leaving everything else untouched. */
  public static String legacyToMiniMessage(final String text) {
    final Matcher matcher = LEGACY_CODE.matcher(text);
    final StringBuilder result = new StringBuilder(text.length());
    while (matcher.find()) {
      final String code = matcher.group(1);
      final String tag = code.startsWith("#")
          ? code.toLowerCase(java.util.Locale.ROOT)
          : LEGACY_TAGS.get(Character.toLowerCase(code.charAt(0)));
      matcher.appendReplacement(result, Matcher.quoteReplacement("<" + tag + ">"));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
