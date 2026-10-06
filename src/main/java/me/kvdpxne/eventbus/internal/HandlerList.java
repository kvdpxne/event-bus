package me.kvdpxne.eventbus.internal;

/**
 * Copy-on-write, priority-sorted handler array.
 * Writes synchronize; reads are lock-free via a volatile reference.
 * Sorting uses {@link me.kvdpxne.eventbus.EventPriority#weight()} so
 * inserting new priorities never reorders existing ones.
 *
 * <p>Entries of equal priority preserve insertion order.</p>
 */
public final class HandlerList {

  private static final HandlerEntry[] EMPTY = new HandlerEntry[0];

  private volatile HandlerEntry[] entries = EMPTY;

  public void add(final HandlerEntry entry) {
    synchronized (this) {
      final var old = this.entries;
      final var target = entry.priority().weight();

      var at = 0;
      while (old.length > at && old[at].priority().weight() <= target) {
        at++;
      }

      final var next = new HandlerEntry[old.length + 1];
      System.arraycopy(old, 0, next, 0, at);
      next[at] = entry;
      System.arraycopy(old, at, next, at + 1, old.length - at);
      this.entries = next;
    }
  }

  public void remove(final HandlerEntry entry) {
    synchronized (this) {
      final var old = this.entries;
      var at = -1;
      for (var i = 0; i < old.length; i++) {
        if (old[i] == entry) {
          at = i;
          break;
        }
      }
      if (0 > at) {
        return;
      }
      if (1 == old.length) {
        this.entries = EMPTY;
        return;
      }
      final var next = new HandlerEntry[old.length - 1];
      System.arraycopy(old, 0, next, 0, at);
      System.arraycopy(old, at + 1, next, at, old.length - at - 1);
      this.entries = next;
    }
  }

  public void removeAllOwnedBy(final Object owner) {
    if (null == owner) {
      return;
    }
    synchronized (this) {
      final var old = this.entries;
      var matches = 0;
      for (final var entry : old) {
        if (entry.owner() == owner) {
          matches++;
        }
      }
      if (0 == matches) {
        return;
      }
      if (old.length == matches) {
        this.entries = EMPTY;
        return;
      }
      final var next = new HandlerEntry[old.length - matches];
      var cursor = 0;
      for (final var entry : old) {
        if (entry.owner() != owner) {
          next[cursor++] = entry;
        }
      }
      this.entries = next;
    }
  }

  public void clear() {
    synchronized (this) {
      this.entries = EMPTY;
    }
  }

  public HandlerEntry[] snapshot() {
    return this.entries;
  }
}