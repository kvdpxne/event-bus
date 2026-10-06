package me.kvdpxne.eventbus.internal;

import me.kvdpxne.eventbus.EventPriority;

import java.util.function.Consumer;

/**
 * Package-internal registry row. The {@code handler} takes {@code Object}
 * and casts internally - type safety is guaranteed by dispatching on the
 * exact concrete event class.
 */
public record HandlerEntry(
  EventPriority priority,
  boolean ignoreCancelled,
  Consumer<Object> handler,
  Object owner
) {
}