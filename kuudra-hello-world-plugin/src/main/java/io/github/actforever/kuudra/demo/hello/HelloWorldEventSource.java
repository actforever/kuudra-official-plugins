package io.github.actforever.kuudra.demo.hello;

import io.github.actforever.kuudra.api.component.EventSource;
import io.github.actforever.kuudra.api.event.EventData;
import io.github.actforever.kuudra.api.event.EventEmitter;
import io.github.actforever.kuudra.api.event.KuudraEvent;
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
@io.github.actforever.kuudra.plugin.annotation.ComponentDoc(
        purpose = "按配置周期产生最小 Hello World 事件。",
        usageExample = "intervalMillis: 1000",
        lifecyclePhases = {"initialize: 读取 intervalMillis", "start: 启动周期任务", "stop: 释放调度线程"},
        configuration = @io.github.actforever.kuudra.plugin.annotation.SpecProperty(
                path = "intervalMillis", type = Long.class, defaultValue = "1000",
                description = "相邻两次 hello-world 事件之间的固定延迟，单位毫秒，必须大于 0。",
                examples = {"1000", "5000"}),
        emittedEvents = @io.github.actforever.kuudra.plugin.annotation.EventEmission(
                stage = "每个调度周期", eventType = "hello-world.tick",
                description = "产生 message=hello-world 的事件。",
                dataExample = "{\"hello-world\":{\"message\":\"hello-world\"}}"))
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
