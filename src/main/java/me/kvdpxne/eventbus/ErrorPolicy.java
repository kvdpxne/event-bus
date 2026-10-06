package me.kvdpxne.eventbus;

/**
 * Controls what happens when a handler throws.
 */
public enum ErrorPolicy {

  /**
   * Log the throwable and continue dispatching to the remaining handlers.
   * Default - safe for production.
   */
  LOG_AND_CONTINUE,

  /**
   * Rethrow the throwable wrapped in a {@link RuntimeException}, aborting
   * the rest of the dispatch. Intended for development and testing.
   */
  STOP_AND_PROPAGATE
}