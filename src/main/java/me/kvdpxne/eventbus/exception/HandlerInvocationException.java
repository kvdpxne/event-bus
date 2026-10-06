package me.kvdpxne.eventbus.exception;

/**
 * Wraps any throwable raised by a handler during dispatch.
 * The original throwable is available via {@link #getCause()}.
 */
public final class HandlerInvocationException extends EventBusException {

    public HandlerInvocationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}