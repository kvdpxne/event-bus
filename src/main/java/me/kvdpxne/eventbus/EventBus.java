package me.kvdpxne.eventbus;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import me.kvdpxne.eventbus.exception.BusClosedException;
import me.kvdpxne.eventbus.exception.HandlerBindingException;
import me.kvdpxne.eventbus.exception.HandlerInvocationException;
import me.kvdpxne.eventbus.internal.HandlerBinder;
import me.kvdpxne.eventbus.internal.HandlerEntry;
import me.kvdpxne.eventbus.internal.HandlerList;
import me.kvdpxne.eventbus.internal.ListenerScanner;

/**
 * Framework-agnostic event bus.
 *
 * <p>Properties:</p>
 * <ul>
 *   <li>Sync ({@link #publish}) and async ({@link #publishAsync}) dispatch.
 *       Async uses virtual threads by default; any {@link ExecutorService}
 *       may be injected.</li>
 *   <li>Priority-aware ordering via {@link EventPriority#weight()}.</li>
 *   <li>Cancellation via {@link Cancellable} plus {@code ignoreCancelled};
 *       {@link EventPriority#MONITOR} always sees the event.</li>
 *   <li>Dynamic register/unregister; {@link Subscription} is
 *       {@link AutoCloseable}.</li>
 *   <li>Wildcard subscriptions ({@link #subscribeAny}) for cross-cutting
 *       concerns.</li>
 *   <li>Configurable {@link ErrorPolicy}.</li>
 *   <li>Zero-allocation dispatch on the hot path.</li>
 * </ul>
 */
public final class EventBus implements AutoCloseable {

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  private final Map<Class<? extends Event>, HandlerList> typedLists;
  private final HandlerList wildcardList;

  private final ExecutorService asyncExecutor;
  private final boolean ownsExecutor;
  private final boolean stopOnError;
  private final Logger logger;

  private final AtomicBoolean closed;
  private volatile DispatchListener dispatchListener;

  public EventBus(final Logger logger) {
    this(logger, null, ErrorPolicy.LOG_AND_CONTINUE);
  }

  public EventBus(final Logger logger, final ErrorPolicy errorPolicy) {
    this(logger, null, errorPolicy);
  }

  public EventBus(final Logger logger, final ExecutorService asyncExecutor) {
    this(logger, asyncExecutor, ErrorPolicy.LOG_AND_CONTINUE);
  }

  public EventBus(final Logger logger,
                  final ExecutorService asyncExecutor,
                  final ErrorPolicy errorPolicy) {
    this.logger = Objects.requireNonNull(logger, "logger");
    this.stopOnError = ErrorPolicy.STOP_AND_PROPAGATE
      == Objects.requireNonNull(errorPolicy, "errorPolicy");

    this.typedLists = new ConcurrentHashMap<>();
    this.wildcardList = new HandlerList();
    this.closed = new AtomicBoolean(false);

    if (null == asyncExecutor) {
      this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
      this.ownsExecutor = true;
    } else {
      this.asyncExecutor = asyncExecutor;
      this.ownsExecutor = false;
    }
  }

  // ------------------------------------------------------------------
  //  Registration - annotation scan
  // ------------------------------------------------------------------

  /**
   * Registers every {@link Subscribe}-annotated method of the listener,
   * including {@code private} methods declared anywhere in the class
   * hierarchy up to (but excluding) {@link Object}.
   * The returned subscription removes all of them at once.
   *
   * @throws NullPointerException if {@code listener} is null
   * @throws BusClosedException   if the bus has been closed
   */
  public Subscription register(final Listener listener) {
    Objects.requireNonNull(listener, "listener");
    this.ensureOpen();

    final var listenerClass = listener.getClass();
    final var methods = ListenerScanner.scan(listenerClass);

    if (methods.isEmpty()) {
      this.logger.warning("Listener " + listenerClass.getName()
        + " declares no @Subscribe methods.");
      return new Subscription(() -> { /* no-op */ });
    }

    final MethodHandles.Lookup privateLookup;
    try {
      privateLookup = MethodHandles.privateLookupIn(listenerClass, LOOKUP);
    } catch (final IllegalAccessException exception) {
      throw new HandlerBindingException(
        "Cannot obtain private lookup for " + listenerClass.getName(),
        exception);
    }

    final List<Runnable> unsubscribers = new ArrayList<>(methods.size());

    for (final var method : methods) {
      if (1 != method.getParameterCount()) {
        this.logger.warning("Skipping " + method
          + " - @Subscribe requires exactly one parameter.");
        continue;
      }
      final var parameterType = method.getParameterTypes()[0];
      if (!Event.class.isAssignableFrom(parameterType)) {
        this.logger.warning("Skipping " + method
          + " - parameter must implement BusEvent.");
        continue;
      }

      final var handler = HandlerBinder.bind(privateLookup, method, listener);
      final var annotation = method.getAnnotation(Subscribe.class);

      @SuppressWarnings("unchecked")
      final var eventType = (Class<? extends Event>) parameterType;

      final var entry = new HandlerEntry(
        annotation.priority(),
        annotation.ignoreCancelled(),
        handler,
        listener);

      final var list = this.listFor(eventType);
      list.add(entry);
      unsubscribers.add(() -> list.remove(entry));
    }

    return new Subscription(() -> {
      for (final var action : unsubscribers) {
        action.run();
      }
    });
  }

  // ------------------------------------------------------------------
  //  Registration - programmatic
  // ------------------------------------------------------------------

  public <T extends Event> Subscription subscribe(final Class<T> eventType,
                                                  final Consumer<T> handler) {
    return this.subscribe(eventType, EventPriority.NORMAL, false, handler);
  }

  public <T extends Event> Subscription subscribe(final Class<T> eventType,
                                                  final EventPriority priority,
                                                  final Consumer<T> handler) {
    return this.subscribe(eventType, priority, false, handler);
  }

  public <T extends Event> Subscription subscribe(final Class<T> eventType,
                                                  final EventPriority priority,
                                                  final boolean ignoreCancelled,
                                                  final Consumer<T> handler) {
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(handler, "handler");
    this.ensureOpen();

    @SuppressWarnings("unchecked")
    final var raw = (Consumer<Object>) (Consumer<?>) handler;

    final var entry = new HandlerEntry(priority, ignoreCancelled, raw, null);
    final var list = this.listFor(eventType);
    list.add(entry);

    return new Subscription(() -> list.remove(entry));
  }

  /**
   * Registers a handler that sees every dispatched event.
   * Intended for logging, profiling and other cross-cutting concerns.
   * Wildcard handlers run after typed handlers of the same dispatch.
   */
  public Subscription subscribeAny(final Consumer<Object> handler) {
    return this.subscribeAny(EventPriority.MONITOR, handler);
  }

  public Subscription subscribeAny(final EventPriority priority,
                                   final Consumer<Object> handler) {
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(handler, "handler");
    this.ensureOpen();

    final var entry = new HandlerEntry(priority, false, handler, null);
    this.wildcardList.add(entry);
    return new Subscription(() -> this.wildcardList.remove(entry));
  }

  /**
   * Removes every handler owned by this listener across all event types.
   *
   * @return {@code true} if at least one handler was removed
   */
  public boolean unregisterListener(final Listener listener) {
    if (null == listener) {
      return false;
    }
    var removed = false;
    for (final var list : this.typedLists.values()) {
      list.removeAllOwnedBy(listener);
      removed = true;
    }
    this.wildcardList.removeAllOwnedBy(listener);
    return removed;
  }

  // ------------------------------------------------------------------
  //  Publishing
  // ------------------------------------------------------------------

  /**
   * Dispatches the event on the calling thread.
   */
  public <T extends Event> T publish(final T event) {
    Objects.requireNonNull(event, "event");
    this.dispatch(event.getClass(), event);
    return event;
  }

  /**
   * Dispatches an arbitrary object to wildcard subscribers only.
   * Useful for events that do not implement {@link Event}.
   */
  public <T> T publishRaw(final T event) {
    Objects.requireNonNull(event, "event");
    this.dispatch(null, event);
    return event;
  }

  /**
   * Dispatches the event on the injected executor.
   * After {@link #close()}, calls are logged and dropped without throwing.
   */
  public <T extends Event> void publishAsync(final T event) {
    Objects.requireNonNull(event, "event");
    if (this.closed.get()) {
      this.logger.warning("publishAsync on a closed bus - dropping "
        + event.getClass().getSimpleName() + ".");
      return;
    }
    try {
      this.asyncExecutor.execute(() -> this.publish(event));
    } catch (final RejectedExecutionException exception) {
      this.logger.warning("Async dispatch rejected - bus is shutting down.");
    }
  }

  // ------------------------------------------------------------------
  //  Observability
  // ------------------------------------------------------------------

  public void setDispatchListener(final DispatchListener listener) {
    this.dispatchListener = listener;
  }

  public boolean isClosed() {
    return this.closed.get();
  }

  // ------------------------------------------------------------------
  //  Lifecycle
  // ------------------------------------------------------------------

  @Override
  public void close() {
    if (!this.closed.compareAndSet(false, true)) {
      return;
    }
    if (this.ownsExecutor) {
      this.asyncExecutor.shutdown();
    }
    this.typedLists.clear();
    this.wildcardList.clear();
  }

  // ------------------------------------------------------------------
  //  Internals
  // ------------------------------------------------------------------

  private void dispatch(final Class<? extends Event> eventType, final Object event) {
    if (this.closed.get()) {
      return;
    }

    final var profiler = this.dispatchListener;
    final var startNanos = null == profiler ? 0L : System.nanoTime();
    var ran = 0;

    final Cancellable cancellable = event instanceof Cancellable c ? c : null;

    if (null != eventType) {
      final var list = this.typedLists.get(eventType);
      if (null != list) {
        final var snapshot = list.snapshot();
        for (final var handlerEntry : snapshot) {
          if (this.invoke(handlerEntry, event, cancellable)) {
            ran++;
          }
        }
      }
    }

    final var wildcardSnapshot = this.wildcardList.snapshot();
    for (final var handlerEntry : wildcardSnapshot) {
      if (this.invoke(handlerEntry, event, cancellable)) {
        ran++;
      }
    }

    if (null != profiler) {
      profiler.onDispatch(
        null == eventType ? event.getClass() : eventType,
        ran,
        System.nanoTime() - startNanos);
    }
  }

  /**
   * @return {@code true} if the handler was invoked (not skipped)
   */
  private boolean invoke(final HandlerEntry entry, final Object event,
                         final Cancellable cancellable) {
    if (null != cancellable
      && entry.ignoreCancelled()
      && cancellable.isCancelled()
      && EventPriority.MONITOR != entry.priority()) {
      return false;
    }

    try {
      entry.handler().accept(event);
      return true;
    } catch (final Throwable throwable) {
      this.onHandlerFailure(event, throwable);
      return true;
    }
  }

  private void onHandlerFailure(final Object event, final Throwable throwable) {
    final var cause = null != throwable.getCause() ? throwable.getCause() : throwable;
    final var exception = new HandlerInvocationException(
      "Handler failed during " + event.getClass().getSimpleName(),
      cause);

    if (this.stopOnError) {
      throw exception;
    }
    this.logger.log(Level.SEVERE, exception.getMessage(), cause);
  }

  private HandlerList listFor(final Class<? extends Event> type) {
    return this.typedLists.computeIfAbsent(type, key -> new HandlerList());
  }

  private void ensureOpen() {
    if (this.closed.get()) {
      throw new BusClosedException("EventBus has been closed.");
    }
  }
}