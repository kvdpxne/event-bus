package me.kvdpxne.eventbus;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handle for a registered handler. {@link #unregister()} is idempotent,
 * and {@link #close()} is an alias so the subscription can be used in
 * try-with-resources.
 */
public final class Subscription implements AutoCloseable {

  private final Runnable action;
  private final AtomicBoolean active;

  public Subscription(final Runnable action) {
    if (null == action) {
      throw new NullPointerException("action must not be null");
    }
    this.action = action;
    this.active = new AtomicBoolean(true);
  }

  public void unregister() {
    if (this.active.compareAndSet(true, false)) {
      this.action.run();
    }
  }

  @Override
  public void close() {
    this.unregister();
  }

  public boolean isActive() {
    return this.active.get();
  }
}