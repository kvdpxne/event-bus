package me.kvdpxne.eventbus;

/**
 * Dispatch order. Lower {@link #weight()} runs first.
 * Weights are explicit so that inserting new constants in the middle
 * never shifts the ordering of existing ones.
 *
 * <p>{@link #MONITOR} always runs last and ignores {@code ignoreCancelled},
 * matching the convention used by Bukkit's plugin manager.</p>
 */
public enum EventPriority {

  LOWEST(0),
  LOW(100),
  NORMAL(200),
  HIGH(300),
  HIGHEST(400),
  MONITOR(500);

  private final int weight;

  EventPriority(final int weight) {
    this.weight = weight;
  }

  public int weight() {
    return this.weight;
  }
}