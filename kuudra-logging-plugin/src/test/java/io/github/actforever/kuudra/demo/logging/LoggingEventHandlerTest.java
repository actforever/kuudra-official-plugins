package io.github.actforever.kuudra.demo.logging;

import io.github.actforever.kuudra.api.action.ActionContext;
import io.github.actforever.kuudra.api.context.ExecutionDecision;
import io.github.actforever.kuudra.api.event.EventData;
import io.github.actforever.kuudra.api.event.EventEmitter;
import io.github.actforever.kuudra.api.event.KuudraEvent;
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
                io.github.actforever.kuudra.plugin.annotation.ComponentDoc.class);
        assertEquals(List.of("level", "message", "includeData"), java.util.Arrays.stream(documentation.configuration())
                .map(io.github.actforever.kuudra.plugin.annotation.SpecProperty::path).toList());
        AtomicReference<PluginLogLevel> level = new AtomicReference<>();
        AtomicReference<Map<String, Object>> fields = new AtomicReference<>();
        PluginLogger logger = (current, message, values, error) -> { level.set(current); fields.set(values); };
        PluginResourceRegistry resources = new PluginResourceRegistry() {
            @Override public void register(String name, AutoCloseable resource) { }
            @Override public List<String> names() { return List.of(); }
        };
        LoggingEventHandler handler = new LoggingEventHandler();
        handler.initialize(new PluginComponentContext("event-handler/kuudra-official/event-logger",
                new PluginContext("logging", "kuudra-official", home, resources,
                        PluginRuntimeServices.unavailable(), logger), Map.of())).toCompletableFuture().join();
        KuudraEvent event = KuudraEvent.of("hello", EventData.of("demo", Map.of("message", "hello-world")));
        ActionContext context = new ActionContext(UUID.randomUUID(), "flow", Map.of(), null,
                () -> ExecutionDecision.CONTINUE, ignored -> true, Map.of(), Map.of("level", "WARN", "includeData", true));
        handler.handle(event, context).toCompletableFuture().join();

        assertEquals(PluginLogLevel.WARN, level.get());
        assertEquals("hello", fields.get().get("eventType"));
        assertEquals(event.data().namespaces(), fields.get().get("data"));
    }
}
