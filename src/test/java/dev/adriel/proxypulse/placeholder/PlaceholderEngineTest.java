package dev.adriel.proxypulse.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adriel.proxypulse.animation.Animation;
import dev.adriel.proxypulse.animation.AnimationRegistry;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class PlaceholderEngineTest {

  private AnimationRegistry animations;
  private PlaceholderEngine engine;

  @BeforeEach
  void setUp() throws Exception {
    animations = new AnimationRegistry();
    animations.load(YamlConfigurationLoader.builder().buildAndLoadString("""
        logo:
          change-interval: 100
          texts:
          - "<red>one</red>"
          - "<blue>two</blue>"
        greeting:
          change-interval: 100
          texts:
          - "hi %who%"
        """));
    engine = new PlaceholderEngine(animations);
    engine.register("who", (argument, context) -> "world");
    engine.register("server_online", (argument, context) ->
        argument == null ? "0" : "players-on-" + argument);
  }

  private static String plain(final Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  private Component render(final Component input, final long now) {
    return engine.render(input, new PulseContext(null, null, null, null, now));
  }

  @Test
  @DisplayName("an animation placeholder becomes the frame for the current instant")
  void animationsResolveToTheCurrentFrame() {
    assertEquals("one", plain(render(Component.text("%animation:logo%"), 0L)));
    assertEquals("two", plain(render(Component.text("%animation:logo%"), 100L)));
  }

  @Test
  @DisplayName("the text around a placeholder, and its styling, survive the replacement")
  void surroundingStyleIsPreserved() {
    final Component input = Component.text("Welcome to %animation:logo% land")
        .color(NamedTextColor.GOLD)
        .decorate(TextDecoration.BOLD);
    final Component rendered = render(input, 0L);

    assertEquals("Welcome to one land", plain(rendered));
    assertEquals(NamedTextColor.GOLD, rendered.color());
    assertTrue(rendered.hasDecoration(TextDecoration.BOLD));
  }

  @Test
  @DisplayName("placeholders are replaced inside nested children, not only the top level")
  void nestedChildrenAreRendered() {
    final Component input = Component.text("a ")
        .append(Component.text("%who%").color(NamedTextColor.RED));
    assertEquals("a world", plain(render(input, 0L)));
  }

  @Test
  @DisplayName("several placeholders in one line all resolve")
  void multiplePlaceholdersInOneLine() {
    assertEquals("one and world",
        plain(render(Component.text("%animation:logo% and %who%"), 0L)));
  }

  @Test
  @DisplayName("the argument after the colon reaches the placeholder")
  void argumentsArePassedThrough() {
    assertEquals("players-on-lobby",
        plain(render(Component.text("%server_online:lobby%"), 0L)));
  }

  @Test
  @DisplayName("a placeholder inside an animation frame is resolved too")
  void placeholdersNestInsideAnimationFrames() {
    assertEquals("hi world", plain(render(Component.text("%animation:greeting%"), 0L)));
  }

  @Test
  @DisplayName("unknown placeholders are left on screen instead of vanishing")
  void unknownPlaceholdersAreLeftAlone() {
    assertEquals("%not_a_thing%", plain(render(Component.text("%not_a_thing%"), 0L)));
    assertEquals("%animation:missing%",
        plain(render(Component.text("%animation:missing%"), 0L)));
  }

  @Test
  @DisplayName("text with no placeholder is recognised as such, so it is never tracked")
  void detectionAvoidsTrackingPlainText() {
    assertFalse(PlaceholderEngine.mayContainPlaceholders(Component.text("just text")));
    assertFalse(PlaceholderEngine.mayContainPlaceholders(Component.text("100% cotton")));
    assertTrue(PlaceholderEngine.mayContainPlaceholders(Component.text("hi %player%")));
  }

  @Test
  @DisplayName("a frame that references itself stops instead of recursing forever")
  void selfReferencingAnimationTerminates() throws Exception {
    final AnimationRegistry recursive = new AnimationRegistry();
    recursive.load(YamlConfigurationLoader.builder().buildAndLoadString("""
        loop:
          change-interval: 100
          texts:
          - "%animation:loop%"
        """));
    final PlaceholderEngine looping = new PlaceholderEngine(recursive);

    final Component rendered = looping.render(Component.text("%animation:loop%"),
        new PulseContext(null, null, null, null, 0L));

    assertEquals("%animation:loop%", plain(rendered));
  }

  @Test
  @DisplayName("frames keep their own colours")
  void frameColoursAreApplied() {
    final Component rendered = render(Component.text("%animation:logo%"), 0L);
    assertEquals(NamedTextColor.RED, firstColour(rendered));
  }

  /** The colour may sit on the root or on a child depending on how MiniMessage folds the tags. */
  private static net.kyori.adventure.text.format.TextColor firstColour(final Component component) {
    if (component.color() != null) {
      return component.color();
    }
    return component.children().stream()
        .map(PlaceholderEngineTest::firstColour)
        .filter(java.util.Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Test
  @DisplayName("every animation defined in a file is reachable by name")
  void animationsAreReachable() {
    assertEquals(List.of("logo", "greeting"),
        animations.all().values().stream().map(Animation::name).toList());
  }
}
