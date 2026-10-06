package me.kvdpxne.eventbus.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.kvdpxne.eventbus.Subscribe;

/**
 * Scans a listener class hierarchy (excluding {@link Object}) for
 * methods annotated with {@link Subscribe}.
 *
 * <p>Returns declarations in the order: subclass first, then superclasses,
 * and within each class the order reported by
 * {@link Class#getDeclaredMethods()}. Private and package-private methods
 * are included; access is handled later by the binder.</p>
 *
 * <p>Stateless and pure - no reflection beyond {@code getDeclaredMethods}.</p>
 */
public final class ListenerScanner {

  private static final ClassValue<List<Method>> CACHE = new ClassValue<>() {
    @Override
    protected List<Method> computeValue(final Class<?> type) {
      final var result = new ArrayList<Method>();
      var current = type;
      while (null != current && Object.class != current) {
        for (final var method : current.getDeclaredMethods()) {
          if (method.isAnnotationPresent(Subscribe.class)) {
            result.add(method);
          }
        }
        current = current.getSuperclass();
      }
      return List.copyOf(result);
    }
  };

  private ListenerScanner() {
    // utility class
  }

  public static List<Method> scan(final Class<?> listenerClass) {
    Objects.requireNonNull(listenerClass, "listenerClass");
    return CACHE.get(listenerClass);
  }
}