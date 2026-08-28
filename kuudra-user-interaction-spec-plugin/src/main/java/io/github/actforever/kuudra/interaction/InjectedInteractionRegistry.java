package io.github.actforever.kuudra.interaction;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;

/**
 * Process-local bounded correlation registry shared by capture and simulation plugins through their declared
 * user-interaction-spec dependency. It is deliberately best-effort because operating systems do not expose a
 * portable injection identity through every native hook implementation.
 */
public final class InjectedInteractionRegistry {
    private static final int MAX_ENTRIES = 4096;
    private static final InjectedInteractionRegistry GLOBAL = new InjectedInteractionRegistry();
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    public static InjectedInteractionRegistry global() { return GLOBAL; }

    public synchronized Ticket expect(InteractionSignature signature, Duration lifetime) {
        Objects.requireNonNull(signature, "signature");
        long millis = Objects.requireNonNull(lifetime, "lifetime").toMillis();
        if (millis <= 0) throw new IllegalArgumentException("lifetime must be positive");
        long now = System.nanoTime();
        purge(now);
        while (entries.size() >= MAX_ENTRIES) entries.removeFirst();
        UUID id = UUID.randomUUID();
        entries.addLast(new Entry(id, signature, now + Duration.ofMillis(millis).toNanos()));
        return new Ticket(id, this);
    }

    public synchronized boolean consume(InteractionSignature signature) {
        Objects.requireNonNull(signature, "signature");
        purge(System.nanoTime());
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().signature.equals(signature)) continue;
            iterator.remove();
            return true;
        }
        return false;
    }

    synchronized void cancel(UUID id) { entries.removeIf(entry -> entry.id.equals(id)); }
    synchronized int size() { purge(System.nanoTime()); return entries.size(); }
    private void purge(long now) { entries.removeIf(entry -> entry.expiresAtNanos <= now); }
    private record Entry(UUID id, InteractionSignature signature, long expiresAtNanos) { }

    /** Cancel the expectation when the platform injection fails before an event can be produced. */
    public static final class Ticket implements AutoCloseable {
        private final UUID id;
        private final InjectedInteractionRegistry owner;
        private boolean committed;
        private Ticket(UUID id, InjectedInteractionRegistry owner) { this.id = id; this.owner = owner; }
        public synchronized void commit() { committed = true; }
        @Override public synchronized void close() { if (!committed) owner.cancel(id); }
    }
}
