package me.kvdpxne.eventbus.common;

import me.kvdpxne.eventbus.Event;

/**
 * Optional base class for events used only outside Bukkit.
 * Bukkit-side events should extend {@code org.bukkit.event.Event} directly
 * (or the {@code BukkitBusEvent} helper) instead of this class, since
 * Java does not allow multiple class inheritance.
 */
public abstract class AbstractEvent implements Event {

  private final boolean async;

  protected AbstractEvent() {
    this(false);
  }

  protected AbstractEvent(final boolean async) {
    this.async = async;
  }

  public final boolean isAsync() {
    return this.async;
  }
}