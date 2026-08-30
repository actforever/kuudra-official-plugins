package io.github.actforever.kuudra.defaultplugin;

import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.EventData;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.ResourceContext;
import io.github.actforever.kuudra.plugin.ResourceLifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

abstract class AbstractWindowInterpreter implements io.github.actforever.kuudra.api.component.EventInterpreter, ResourceLifecycle {
    protected String outputType;
    protected long timeoutMs;
    protected boolean includeMatchedEvents;
    protected List<Requirement> requirements;
    protected List<Map<String, Object>> forbidden;
    protected final List<KuudraEvent> matched = new ArrayList<>();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> timeout;

    @Override public CompletionStage<Void> initialize(ResourceContext context) {
        outputType = context.option("outputType", String.class);
        timeoutMs = context.option("timeoutMs", Long.class, 3000L);
        includeMatchedEvents = context.option("includeMatchedEvents", Boolean.class, true);
        if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs must be positive");
        requirements = parseRequirements(context.option("requirements", List.class));
        forbidden = parseSelectors(context.option("forbidden", List.class, List.of()));
        return CompletableFuture.completedFuture(null);
    }

    @Override public synchronized CompletionStage<Void> start() {
        if (scheduler == null || scheduler.isShutdown()) scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "kuudra-event-interpreter"); thread.setDaemon(true); return thread;
        });
        reset();
        return CompletableFuture.completedFuture(null);
    }

    @Override public synchronized CompletionStage<Void> stop() {
        reset();
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = null;
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<Void> destroy() { return stop(); }

    protected boolean forbidden(KuudraEvent event) { return forbidden.stream().anyMatch(selector -> EventSupport.matches(event, selector)); }
    protected void record(KuudraEvent event) {
        if (matched.isEmpty()) timeout = scheduler.schedule(this::expire, timeoutMs, TimeUnit.MILLISECONDS);
        matched.add(event);
    }
    protected List<KuudraEvent> complete(String strategy) {
        EventData data = EventData.of("kuudra-official", Map.of("interpreter", strategy, "matchCount", matched.size()));
        if (includeMatchedEvents) data = data.with("kuudra-official", "matchedEvents", matched.stream().map(e -> Map.of("id", e.id().toString(), "type", e.type(), "data", e.data().namespaces())).toList());
        KuudraEvent output = new KuudraEvent(UUID.randomUUID(), outputType, java.time.Instant.now(), data, matched.get(0).lineage());
        reset();
        return List.of(output);
    }
    protected synchronized void reset() {
        if (timeout != null) timeout.cancel(false);
        timeout = null; matched.clear(); resetProgress();
    }
    private synchronized void expire() { timeout = null; matched.clear(); resetProgress(); }
    protected abstract void resetProgress();

    @SuppressWarnings("unchecked")
    private List<Requirement> parseRequirements(List<?> raw) {
        if (raw == null || raw.isEmpty()) throw new IllegalArgumentException("requirements must not be empty");
        List<Requirement> result = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> map) || !(map.get("selector") instanceof Map<?, ?> selector)) throw new IllegalArgumentException("Each requirement needs a selector object");
            int count = map.get("count") == null ? 1 : Integer.parseInt(map.get("count").toString());
            if (count <= 0) throw new IllegalArgumentException("requirement count must be positive");
            result.add(new Requirement((Map<String, Object>) Map.copyOf(selector), count));
        }
        return List.copyOf(result);
    }
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseSelectors(List<?> raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> map)) throw new IllegalArgumentException("Each forbidden selector must be an object");
            result.add((Map<String, Object>) Map.copyOf(map));
        }
        return List.copyOf(result);
    }
    protected record Requirement(Map<String, Object> selector, int count) {}
}
