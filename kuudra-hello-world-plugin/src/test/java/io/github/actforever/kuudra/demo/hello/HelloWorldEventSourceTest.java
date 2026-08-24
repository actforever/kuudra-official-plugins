package io.github.actforever.kuudra.demo.hello;

import io.github.actforever.kuudra.api.KuudraEvent;
import io.github.actforever.kuudra.plugin.PluginComponentContext;
import io.github.actforever.kuudra.plugin.PluginContext;
import io.github.actforever.kuudra.plugin.PluginResourceRegistry;
import io.github.actforever.kuudra.plugin.PluginRuntimeServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HelloWorldEventSourceTest {
    @TempDir Path home;

    @Test
    void emitsConfiguredHelloWorldEvent() throws Exception {
        HelloWorldEventSource source = new HelloWorldEventSource();
        PluginResourceRegistry resources = new PluginResourceRegistry() {
            @Override public void register(String name, AutoCloseable resource) { }
            @Override public List<String> names() { return List.of(); }
        };
        source.initialize(new PluginComponentContext("event-source/demo-plugins/hello-world",
                new PluginContext("hello-world", home, resources, PluginRuntimeServices.unavailable()),
                Map.of("intervalMillis", 10)));
        LinkedBlockingQueue<KuudraEvent> events = new LinkedBlockingQueue<>();
        source.setEmitter(events::offer);
        try {
            source.start().toCompletableFuture().join();
            KuudraEvent event = events.poll(1, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals(HelloWorldEventSource.EVENT_TYPE, event.type());
            assertEquals("hello-world", event.data().get(HelloWorldEventSource.DATA_NAMESPACE,
                    HelloWorldEventSource.MESSAGE_KEY, String.class));
        } finally {
            source.stop().toCompletableFuture().join();
        }
    }
}
