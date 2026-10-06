package me.kvdpxne.eventbus.exception;

/**
 * Thrown when a {@code @Subscribe} method cannot be bound to a
 * {@link java.lang.invoke.MethodHandle} - typically because of access
 * restrictions or an unsupported signature.
 */
public final class HandlerBindingException extends EventBusException {

    public HandlerBindingException(final String message) {
        super(message);
    }

    public HandlerBindingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}