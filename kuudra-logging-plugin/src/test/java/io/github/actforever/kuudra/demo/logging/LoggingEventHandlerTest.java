package io.github.actforever.kuudra.demo.logging;

import io.github.actforever.kuudra.api.context.*;
import io.github.actforever.kuudra.api.event.EventData;
import io.github.actforever.kuudra.api.event.EventEmitter;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.api.event.EventHandlerContext;
import io.github.actforever.kuudra.api.session.CurrentSessionControl;
import io.github.actforever.kuudra.plugin.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoggingEventHandlerTest {
    @TempDir Path home;

    @Test
    void logsEventThroughIdentityBoundPluginLogger() {
        var documentation = LoggingEventHandler.class.getAnnotation(
                io.github.actforever.kuudra.plugin.annotation.ResourceDoc.class);
        assertEquals(List.of("level", "message", "includeData"), java.util.Arrays.stream(documentation.arguments())
                .map(io.github.actforever.kuudra.plugin.annotation.SpecProperty::path).toList());
        AtomicReference<PluginLogLevel> level = new AtomicReference<>();
        AtomicReference<Map<String, Object>> fields = new AtomicReference<>();
        PluginLogger logger = (current, message, values, error) -> { level.set(current); fields.set(values); };
        PluginResourceRegistry resources = new PluginResourceRegistry() {
            @Override public void register(String name, AutoCloseable resource) { }
            @Override public List<String> names() { return List.of(); }
        };
        LoggingEventHandler handler = new LoggingEventHandler();
        handler.initialize(new ResourceContext("controller/kuudra-official/event-logger",
                new PluginContext("logging", "kuudra-official", home, resources,
                        PluginRuntimeServices.unavailable(), logger), Map.of())).toCompletableFuture().join();
        KuudraEvent event = KuudraEvent.of("hello", EventData.of("demo", Map.of("message", "hello-world")));
        EventHandlerContext context = context(Map.of("level", "WARN", "includeData", true));
        handler.handle(event, context).toCompletableFuture().join();

        assertEquals(PluginLogLevel.WARN, level.get());
        assertEquals("hello", fields.get().get("eventType"));
        assertEquals(event.data().namespaces(), fields.get().get("data"));
    }

    private static EventHandlerContext context(Map<String, Object> arguments) {
        UUID id = UUID.randomUUID(); EmptyContext values = new EmptyContext();
        return new EventHandlerContext() {
            @Override public UUID sessionId() { return id; }
            @Override public String abilityId() { return "demo/logging"; }
            @Override public long abilityRevision() { return 1; }
            @Override public String nodeId() { return "log"; }
            @Override public String handlerName() { return "log"; }
            @Override public SessionContext session() { return values; }
            @Override public AbilityContext ability() { return values; }
            @Override public GlobalContext global() { return values; }
            @Override public TypedValueMap arguments() { return TypedValueMap.of(arguments); }
            @Override public ExecutionControl executionControl() { return () -> ExecutionDecision.CONTINUE; }
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
