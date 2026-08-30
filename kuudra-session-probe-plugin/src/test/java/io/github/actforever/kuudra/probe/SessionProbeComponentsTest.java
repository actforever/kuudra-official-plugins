package io.github.actforever.kuudra.probe;

import io.github.actforever.kuudra.api.context.*;
import io.github.actforever.kuudra.api.event.EventHandlerContext;
import io.github.actforever.kuudra.api.event.EventEmitter;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.api.session.CurrentSessionControl;
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
        EventHandlerContext context = handlerContext(Map.of("durationMillis", 10_000L, "label", "job"));

        handler.handle(event, context).toCompletableFuture().join();
        handler.destroy().toCompletableFuture().join();

        assertEquals("probe.cancelled", message.get());
    }

    private ResourceContext componentContext(Map<String, Object> configuration, PluginLogger logger) {
        PluginResourceRegistry resources = new PluginResourceRegistry() {
            @Override public void register(String name, AutoCloseable resource) { }
            @Override public List<String> names() { return List.of(); }
        };
        PluginContext plugin = new PluginContext("session-probe", "kuudra-official", home, resources,
                PluginRuntimeServices.unavailable(), logger);
        return new ResourceContext("event-source/kuudra-official/session-probe", plugin, configuration);
    }

    private static EventHandlerContext handlerContext(Map<String, Object> arguments) {
        UUID id = UUID.randomUUID(); EmptyContext values = new EmptyContext();
        return new EventHandlerContext() {
            @Override public UUID sessionId() { return id; }
            @Override public String abilityId() { return "demo/probe"; }
            @Override public long abilityRevision() { return 1; }
            @Override public String nodeId() { return "hold"; }
            @Override public String handlerName() { return "hold"; }
            @Override public SessionContext session() { return values; }
            @Override public AbilityContext ability() { return values; }
            @Override public GlobalContext global() { return values; }
            @Override public TypedValueMap arguments() { return TypedValueMap.of(arguments); }
            @Override public ExecutionControl executionControl() { return () -> ExecutionDecision.CANCEL; }
            @Override public CurrentSessionControl sessionControl() { return CurrentSessionControl.unavailable(id); }
            @Override public boolean emit(KuudraEvent event) { return true; }
        };
    }

    private static final class EmptyContext implements SessionContext, AbilityContext, GlobalContext {
        @Override public Map<String, Object> snapshot() { return Map.of(); }
        @Override public boolean compareAndSet(Map<String, Object> expected, Map<String, Object> replacement) { return false; }
        @Override public Map<String, Object> update(java.util.function.UnaryOperator<Map<String, Object>> operation) { return Map.of(); }
    }
}
