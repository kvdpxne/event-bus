package me.kvdpxne.eventbus;

/**
 * Optional observer invoked once per dispatch call.
 * Enables lightweight profiling without touching the bus internals.
 * Called on the dispatching thread, after all handlers finished.
 */
@FunctionalInterface
public interface DispatchListener {

  /**
   * @param eventType    concrete event class that was dispatched
   * @param handlerCount number of handlers that ran
   * @param nanos        wall-clock duration of the dispatch
   */
  void onDispatch(Class<?> eventType, int handlerCount, long nanos);
}