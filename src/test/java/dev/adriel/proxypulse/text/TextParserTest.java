package dev.adriel.proxypulse.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextParserTest {

  private static String plain(final Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  @Test
  @DisplayName("legacy ampersand codes become MiniMessage tags")
  void legacyCodesAreTranslated() {
    assertEquals("<green>Hello", TextParser.legacyToMiniMessage("&aHello"));
    assertEquals("<bold><red>Hi", TextParser.legacyToMiniMessage("&l&cHi"));
  }

  @Test
  @DisplayName("legacy hex codes are translated as well")
  void legacyHexIsTranslated() {
    assertEquals("<#ff8800>Warm", TextParser.legacyToMiniMessage("&#FF8800Warm"));
  }

  @Test
  @DisplayName("a line mixing both styles parses as one component")
  void mixedStylesParse() {
    final Component parsed = TextParser.parse("&7Ping: <green>low");
    assertEquals("Ping: low", plain(parsed));
  }

  @Test
  @DisplayName("colours survive the round trip")
  void coloursSurvive() {
    assertEquals(NamedTextColor.GREEN, firstColour(TextParser.parse("&aok")));
    assertEquals(TextColor.fromHexString("#5481c8"),
        firstColour(TextParser.parse("&#5481C8ok")));
  }

  /** The colour may sit on the root or on a child depending on how MiniMessage folds the tags. */
  static TextColor firstColour(final Component component) {
    if (component.color() != null) {
      return component.color();
    }
    return component.children().stream()
        .map(TextParserTest::firstColour)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Test
  @DisplayName("plain text without codes is left exactly as written")
  void plainTextIsUntouched() {
    assertEquals("just text", plain(TextParser.parse("just text")));
    assertEquals("100% cotton", plain(TextParser.parse("100% cotton")));
  }
}
