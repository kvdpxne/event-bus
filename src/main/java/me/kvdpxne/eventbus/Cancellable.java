package me.kvdpxne.eventbus;

/**
 * Cancellation contract, framework-agnostic.
 * An event may implement this alongside any other Cancellable interface -
 * a single {@code isCancelled}/{@code setCancelled} pair satisfies both.
 */
public interface Cancellable {

  boolean isCancelled();

  void setCancelled(boolean cancelled);
}