package dev.adriel.proxypulse.animation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The single heartbeat behind every moving piece of text on the network.
 *
 * <p>One thread ticks at the shortest interval a Minecraft client can meaningfully render, and each
 * subscriber decides for itself whether anything actually changed since the last tick. A scheduler
 * per animation, or per player, would multiply threads for no gain: the frame to show is a pure
 * function of the clock, so a single shared tick is enough for all of them.
 */
public final class AnimationTicker {

  /** One Minecraft tick. The fastest an animation is allowed to change. */
  public static final long TICK_INTERVAL_MILLIS = 50L;

  private final Set<Refreshable> subscribers = ConcurrentHashMap.newKeySet();
  private final Consumer<Throwable> errorHandler;

  private ScheduledExecutorService executor;

  public AnimationTicker(final Consumer<Throwable> errorHandler) {
    this.errorHandler = errorHandler;
  }

  /** Something that may need to redraw itself when the clock moves. */
  @FunctionalInterface
  public interface Refreshable {
    void refresh(long timestampMillis);
  }

  public synchronized void start() {
    if (executor != null) {
      return;
    }
    executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      final Thread thread = new Thread(runnable, "proxypulse-ticker");
      thread.setDaemon(true);
      return thread;
    });
    executor.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MILLIS, TICK_INTERVAL_MILLIS,
        TimeUnit.MILLISECONDS);
  }

  public synchronized void stop() {
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
    subscribers.clear();
  }

  public void subscribe(final Refreshable refreshable) {
    subscribers.add(refreshable);
  }

  public void unsubscribe(final Refreshable refreshable) {
    subscribers.remove(refreshable);
  }

  public int subscriberCount() {
    return subscribers.size();
  }

  /** Runs one tick. Visible for testing so the timing can be driven by hand. */
  void tick() {
    final long now = System.currentTimeMillis();
    for (final Refreshable subscriber : subscribers) {
      try {
        subscriber.refresh(now);
      } catch (final Throwable throwable) {
        // One broken element must not stop every other player's animations.
        errorHandler.accept(throwable);
      }
    }
  }
}
