package me.kvdpxne.eventbus.exception;

/**
 * Thrown when an operation requiring an open bus is invoked after
 * {@link me.kvdpxne.eventbus.EventBus#close()}.
 */
public final class BusClosedException extends EventBusException {

    public BusClosedException(final String message) {
        super(message);
    }
}