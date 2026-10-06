package me.kvdpxne.eventbus.exception;

/**
 * Base class for every exception thrown by the event bus.
 * Unchecked so it can travel through {@link java.util.function.Consumer}
 * and other functional interfaces without declaration noise.
 */
public class EventBusException extends RuntimeException {

  public EventBusException(final String message) {
    super(message);
  }

  public EventBusException(final String message, final Throwable cause) {
    super(message, cause);
  }
}