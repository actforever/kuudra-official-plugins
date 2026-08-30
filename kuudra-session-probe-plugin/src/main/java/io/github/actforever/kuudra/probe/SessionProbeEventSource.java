package io.github.actforever.kuudra.probe;

import io.github.actforever.kuudra.api.KuudraException;
import io.github.actforever.kuudra.api.component.EventSource;
import io.github.actforever.kuudra.api.event.EventData;
import io.github.actforever.kuudra.api.event.EventEmitter;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.ResourceContext;
import io.github.actforever.kuudra.plugin.ResourceLifecycle;
import io.github.actforever.kuudra.plugin.annotation.ResourceDoc;
import io.github.actforever.kuudra.plugin.annotation.EventEmission;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@io.github.actforever.kuudra.plugin.annotation.EventSource("session-probe-source")
@ResourceDoc(purpose = "Emits a finite, deterministic Event sequence for Session scheduling and dependency diagnostics.",
        lifecyclePhases = {"initialize: validate timing and Event metadata", "start: emit the configured sequence", "stop: cancel pending emissions"},
        options = {
                @SpecProperty(path = "eventType", type = String.class, defaultValue = "\"session-probe.tick\"", description = "Emitted Event type."),
                @SpecProperty(path = "role", type = String.class, defaultValue = "\"probe\"", description = "Role copied to event#session-probe.role."),
                @SpecProperty(path = "groupKey", type = String.class, defaultValue = "\"default\"", description = "Stable value copied to event#session-probe.groupKey."),
                @SpecProperty(path = "initialDelayMillis", type = Long.class, defaultValue = "0", description = "Delay before the first Event."),
                @SpecProperty(path = "intervalMillis", type = Long.class, defaultValue = "1000", description = "Delay between Events."),
                @SpecProperty(path = "maxEvents", type = Long.class, defaultValue = "1", description = "Finite number of Events emitted per start.")
        },
        emittedEvents = @EventEmission(stage = "each configured interval", eventType = "session-probe.tick",
                description = "Carries role, groupKey and a monotonically increasing sequence.",
                dataExample = "{\"session-probe\":{\"role\":\"window\",\"groupKey\":\"window\",\"sequence\":1}}"))
public final class SessionProbeEventSource implements EventSource, ResourceLifecycle {
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicLong sequence = new AtomicLong();
    private volatile EventEmitter emitter;
    private volatile ScheduledExecutorService scheduler;
    private String eventType;
    private String role;
    private String groupKey;
    private long initialDelayMillis;
    private long intervalMillis;
    private long maxEvents;

    @Override public CompletionStage<Void> initialize(ResourceContext context) {
        eventType = context.option("eventType", String.class, "session-probe.tick");
        role = context.option("role", String.class, "probe");
        groupKey = context.option("groupKey", String.class, "default");
        initialDelayMillis = context.option("initialDelayMillis", Long.class, 0L);
        intervalMillis = context.option("intervalMillis", Long.class, 1_000L);
        maxEvents = context.option("maxEvents", Long.class, 1L);
        if (initialDelayMillis < 0 || intervalMillis < 1 || maxEvents < 1) {
            throw new KuudraException("Probe timing requires initialDelayMillis >= 0, intervalMillis > 0 and maxEvents > 0");
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override public void setEmitter(EventEmitter emitter) { this.emitter = Objects.requireNonNull(emitter, "emitter"); }

    @Override public CompletionStage<Void> start() {
        if (!started.compareAndSet(false, true)) return CompletableFuture.completedFuture(null);
        if (emitter == null) {
            started.set(false);
            return CompletableFuture.failedFuture(new KuudraException("SessionProbeEventSource emitter is not configured"));
        }
        sequence.set(0);
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "kuudra-session-probe-source");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.schedule(this::emitNext, initialDelayMillis, TimeUnit.MILLISECONDS);
        return CompletableFuture.completedFuture(null);
    }

    private void emitNext() {
        long current = sequence.incrementAndGet();
        emitter.emit(KuudraEvent.of(eventType, EventData.of("session-probe",
                Map.of("role", role, "groupKey", groupKey, "sequence", current))));
        if (current < maxEvents) {
            ScheduledExecutorService currentScheduler = scheduler;
            if (currentScheduler != null && !currentScheduler.isShutdown()) {
                currentScheduler.schedule(this::emitNext, intervalMillis, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override public CompletionStage<Void> stop() {
        ScheduledExecutorService current = scheduler;
        scheduler = null;
        if (current != null) current.shutdownNow();
        started.set(false);
        return CompletableFuture.completedFuture(null);
    }
}
