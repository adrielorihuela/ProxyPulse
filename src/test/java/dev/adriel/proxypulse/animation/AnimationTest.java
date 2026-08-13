package dev.adriel.proxypulse.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnimationTest {

  private static final Animation ANIMATION =
      new Animation("logo", 100L, List.of("a", "b", "c"));

  @Test
  @DisplayName("the frame on screen is derived from the clock, so it cycles and wraps around")
  void framesCycleWithTheClock() {
    assertEquals("a", ANIMATION.frameAt(0L));
    assertEquals("a", ANIMATION.frameAt(99L));
    assertEquals("b", ANIMATION.frameAt(100L));
    assertEquals("c", ANIMATION.frameAt(250L));
    assertEquals("a", ANIMATION.frameAt(300L));
  }

  @Test
  @DisplayName("two players asking at the same instant get the same frame")
  void framesAreTheSameForEveryone() {
    final long instant = 1_735_689_600_123L;
    assertEquals(ANIMATION.frameIndexAt(instant), ANIMATION.frameIndexAt(instant));
  }

  @Test
  @DisplayName("an animation with no texts is rejected instead of loading empty")
  void emptyAnimationIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new Animation("empty", 100L, List.of()));
  }

  @Test
  @DisplayName("an interval faster than a client can render is rejected")
  void tooFastAnimationIsRejected() {
    final IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
        () -> new Animation("fast", 10L, List.of("a")));
    assertEquals(true, thrown.getMessage().contains("change-interval"));
  }
}
