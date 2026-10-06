package me.kvdpxne.eventbus.internal;

import me.kvdpxne.eventbus.Event;
import me.kvdpxne.eventbus.exception.HandlerBindingException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Binds a {@link Method} to a {@link Consumer} that can be invoked
 * against any {@link Event}. Isolates the reflection and
 * {@link MethodHandle} plumbing from dispatch.
 */
public final class HandlerBinder {

  private static final MethodType HANDLER_TYPE =
    MethodType.methodType(void.class, Event.class);

  private HandlerBinder() {
    // utility class
  }

  public static Consumer<Object> bind(final MethodHandles.Lookup lookup,
                                      final Method method,
                                      final Object listener) {
    try {
      method.setAccessible(true);

      final var handle = lookup.unreflect(method)
        .bindTo(listener)
        .asType(HANDLER_TYPE);

      return event -> {
        try {
          handle.invokeExact((Event) event);
        } catch (final Throwable throwable) {
          throw new HandlerInvocationExceptionImpl(throwable);
        }
      };
    } catch (final IllegalAccessException exception) {
      throw new HandlerBindingException(
        "Cannot bind handler " + method, exception);
    }
  }

  /**
   * Private marker used to signal a failure raised by a handler body.
   * Kept separate from {@code HandlerInvocationException} so that a user
   * handler throwing the same class is not confused with a bus wrapper.
   */
  private static final class HandlerInvocationExceptionImpl
    extends RuntimeException {
    HandlerInvocationExceptionImpl(final Throwable cause) {
      super(cause);
    }
  }
}