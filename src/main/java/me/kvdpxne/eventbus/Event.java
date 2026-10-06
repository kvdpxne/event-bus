package me.kvdpxne.eventbus;

/**
 * Marker for events dispatched through {@link EventBus}.
 *
 * <p>Framework-agnostic on purpose: an event class may implement this
 * interface on top of any base class (or no base class at all). When the
 * same class also extends {@code org.bukkit.event.Event}, it becomes
 * dispatchable through both systems without any adapter inside the event.</p>
 */
public interface Event {
}