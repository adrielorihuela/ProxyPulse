package dev.adriel.proxypulse.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

class AnimationRegistryTest {

  private static ConfigurationNode parse(final String yaml) throws IOException {
    return YamlConfigurationLoader.builder().buildAndLoadString(yaml);
  }

  @Test
  @DisplayName("reads the format the TAB plugin uses, verbatim")
  void readsTabStyleAnimations() throws Exception {
    final AnimationRegistry registry = new AnimationRegistry();
    final List<String> problems = registry.load(parse("""
        logo:
          change-interval: 50
          texts:
          - "<gradient:#5481C8:#54C8EB>Birdflop</gradient>"
          - "<gradient:#5493D1:#54DAF4>Birdflop</gradient>"
        """));

    assertTrue(problems.isEmpty(), () -> "unexpected problems: " + problems);
    assertEquals(1, registry.size());

    final Animation logo = registry.find("logo").orElseThrow();
    assertEquals(50L, logo.changeInterval());
    assertEquals(2, logo.frames().size());
    assertEquals("<gradient:#5481C8:#54C8EB>Birdflop</gradient>", logo.frameAt(0L));
  }

  @Test
  @DisplayName("animations are looked up regardless of the case used in the placeholder")
  void lookupIsCaseInsensitive() throws Exception {
    final AnimationRegistry registry = new AnimationRegistry();
    registry.load(parse("MyLogo:\n  change-interval: 100\n  texts:\n  - \"x\"\n"));

    assertTrue(registry.find("mylogo").isPresent());
    assertTrue(registry.find("MYLOGO").isPresent());
  }

  @Test
  @DisplayName("one broken animation is skipped, the rest still load")
  void brokenAnimationDoesNotBreakTheFile() throws Exception {
    final AnimationRegistry registry = new AnimationRegistry();
    final List<String> problems = registry.load(parse("""
        broken:
          change-interval: 100
        toofast:
          change-interval: 1
          texts:
          - "x"
        good:
          change-interval: 100
          texts:
          - "y"
        """));

    assertEquals(2, problems.size(), () -> "problems were: " + problems);
    assertEquals(1, registry.size());
    assertTrue(registry.find("good").isPresent());
    assertFalse(registry.find("broken").isPresent());
  }
}
