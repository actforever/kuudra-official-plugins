package io.github.actforever.kuudra.demo.hello;

import io.github.actforever.kuudra.api.EventData;
import io.github.actforever.kuudra.api.EventEmitter;
import io.github.actforever.kuudra.api.EventSource;
import io.github.actforever.kuudra.api.KuudraEvent;
import io.github.actforever.kuudra.api.KuudraException;
import io.github.actforever.kuudra.plugin.PluginComponentContext;
import io.github.actforever.kuudra.plugin.PluginComponentLifecycle;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@io.github.actforever.kuudra.plugin.annotation.EventSource("hello-world")
public final class HelloWorldEventSource implements EventSource, PluginComponentLifecycle {
    public static final String EVENT_TYPE = "hello-world.tick";
    public static final String DATA_NAMESPACE = "hello-world";
    public static final String MESSAGE_KEY = "message";
    private static final long DEFAULT_INTERVAL_MILLIS = 1_000L;

    private final AtomicBoolean started = new AtomicBoolean();
    private volatile EventEmitter emitter;
    private volatile long intervalMillis = DEFAULT_INTERVAL_MILLIS;
    private volatile ScheduledExecutorService scheduler;

    @Override
    public CompletionStage<Void> initialize(PluginComponentContext context) {
        intervalMillis = context.configuration("intervalMillis", Long.class, DEFAULT_INTERVAL_MILLIS);
        if (intervalMillis < 1) throw new KuudraException("intervalMillis must be positive");
        return CompletableFuture.completedFuture(null);
    }

    @Override public void setEmitter(EventEmitter emitter) { this.emitter = Objects.requireNonNull(emitter, "emitter"); }

    @Override
    public CompletionStage<Void> start() {
        if (!started.compareAndSet(false, true)) return CompletableFuture.completedFuture(null);
        if (emitter == null) {
            started.set(false);
            return CompletableFuture.failedFuture(new KuudraException("HelloWorldEventSource emitter is not configured"));
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "kuudra-hello-world-source");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::emit, 0, intervalMillis, TimeUnit.MILLISECONDS);
        return CompletableFuture.completedFuture(null);
    }

    private void emit() {
        emitter.emit(KuudraEvent.of(EVENT_TYPE,
                EventData.of(DATA_NAMESPACE, Map.of(MESSAGE_KEY, "hello-world"))));
    }

    @Override
    public CompletionStage<Void> stop() {
        ScheduledExecutorService current = scheduler;
        scheduler = null;
        if (current != null) current.shutdownNow();
        started.set(false);
        return CompletableFuture.completedFuture(null);
    }
}
