package io.github.actforever.kuudra.probe;

import io.github.actforever.kuudra.api.action.ActionContext;
import io.github.actforever.kuudra.api.context.ExecutionDecision;
import io.github.actforever.kuudra.api.event.EventEmitter;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SessionProbeComponentsTest {
    @TempDir Path home;

    @Test
    void sourceEmitsFiniteTypedProbeEvent() throws Exception {
        SessionProbeEventSource source = new SessionProbeEventSource();
        source.initialize(componentContext(Map.of("role", "window", "groupKey", "main", "maxEvents", 1L),
                        (level, text, fields, error) -> { }))
                .toCompletableFuture().join();
        AtomicReference<KuudraEvent> emitted = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        source.setEmitter(event -> { emitted.set(event); latch.countDown(); return true; });

        source.start().toCompletableFuture().join();
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        source.stop().toCompletableFuture().join();

        assertEquals("session-probe.tick", emitted.get().type());
        assertEquals("window", emitted.get().data().get("session-probe", "role", String.class));
        assertEquals("main", emitted.get().data().get("session-probe", "groupKey", String.class));
        assertEquals(1L, emitted.get().data().get("session-probe", "sequence", Long.class));
    }

    @Test
    void handlerObservesCooperativeCancellation() {
        AtomicReference<String> message = new AtomicReference<>();
        SessionProbeEventHandler handler = new SessionProbeEventHandler();
        handler.initialize(componentContext(Map.of(), (level, text, fields, error) -> message.set(text)))
                .toCompletableFuture().join();
        KuudraEvent event = KuudraEvent.of("probe", Map.of());
        ActionContext context = new ActionContext(UUID.randomUUID(), "demo/job-flow", Map.of(), null,
                () -> ExecutionDecision.CANCEL, ignored -> true, Map.of(),
                Map.of("durationMillis", 10_000L, "label", "job"));

        handler.handle(event, context).toCompletableFuture().join();
        handler.destroy().toCompletableFuture().join();

        assertEquals("probe.cancelled", message.get());
    }

    private PluginComponentContext componentContext(Map<String, Object> configuration, PluginLogger logger) {
        PluginResourceRegistry resources = new PluginResourceRegistry() {
            @Override public void register(String name, AutoCloseable resource) { }
            @Override public List<String> names() { return List.of(); }
        };
        PluginContext plugin = new PluginContext("session-probe", "kuudra-official", home, resources,
                PluginRuntimeServices.unavailable(), logger);
        return new PluginComponentContext("session-probe", plugin, configuration);
    }
}
