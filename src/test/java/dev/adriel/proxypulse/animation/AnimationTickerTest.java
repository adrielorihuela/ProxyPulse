package dev.adriel.proxypulse.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnimationTickerTest {

  @Test
  @DisplayName("a subscriber that throws does not stop the others from refreshing")
  void oneBrokenSubscriberDoesNotStopTheRest() {
    final AtomicReference<Throwable> reported = new AtomicReference<>();
    final AnimationTicker ticker = new AnimationTicker(reported::set);
    final AtomicInteger healthyRefreshes = new AtomicInteger();

    ticker.subscribe(timestamp -> {
      throw new IllegalStateException("boom");
    });
    ticker.subscribe(timestamp -> healthyRefreshes.incrementAndGet());

    ticker.tick();

    assertEquals(1, healthyRefreshes.get());
    assertTrue(reported.get() instanceof IllegalStateException);
  }

  @Test
  @DisplayName("unsubscribing stops the refreshes, so a leaving player costs nothing")
  void unsubscribedElementsAreNotRefreshed() {
    final AnimationTicker ticker = new AnimationTicker(throwable -> { });
    final AtomicInteger refreshes = new AtomicInteger();
    final AnimationTicker.Refreshable element = timestamp -> refreshes.incrementAndGet();

    ticker.subscribe(element);
    ticker.tick();
    ticker.unsubscribe(element);
    ticker.tick();

    assertEquals(1, refreshes.get());
    assertEquals(0, ticker.subscriberCount());
  }
}
