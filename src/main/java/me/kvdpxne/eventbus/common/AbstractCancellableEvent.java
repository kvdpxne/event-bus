package me.kvdpxne.eventbus.common;

import me.kvdpxne.eventbus.Cancellable;

public class AbstractCancellableEvent extends AbstractEvent implements Cancellable {

    private boolean cancel;

    @Override
    public boolean isCancelled() {
        return this.cancel;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancel = cancelled;
    }
}
