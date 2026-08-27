package io.github.actforever.kuudra.probe;

import io.github.actforever.kuudra.api.action.ActionContext;
import io.github.actforever.kuudra.api.component.EventHandler;
import io.github.actforever.kuudra.api.context.ExecutionDecision;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.*;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.Map;
import java.util.concurrent.*;

@io.github.actforever.kuudra.plugin.annotation.EventHandler("session-probe-handler")
@ComponentDoc(purpose = "Keeps a Session lease for a configured duration and reports completion or cooperative cancellation.",
        lifecyclePhases = {"initialize: create the probe timer", "handle: checkpoint until duration or cancellation", "destroy: stop the timer"},
        configuration = {
                @SpecProperty(path = "durationMillis", type = Long.class, defaultValue = "1000", description = "How long the handler keeps the Session lease."),
                @SpecProperty(path = "checkpointIntervalMillis", type = Long.class, defaultValue = "25", description = "Maximum delay before observing pause or cancellation."),
                @SpecProperty(path = "label", type = String.class, defaultValue = "\"probe\"", description = "Label included in diagnostic plugin logs.")
        })
public final class SessionProbeEventHandler implements EventHandler, PluginComponentLifecycle {
    private volatile ScheduledExecutorService scheduler;
    private PluginLogger logger;

    @Override public CompletionStage<Void> initialize(PluginComponentContext context) {
        logger = context.logger();
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "kuudra-session-probe-handler");
            thread.setDaemon(true);
            return thread;
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<Void> handle(KuudraEvent event, ActionContext context) {
        long duration = context.configuration("durationMillis", Long.class, 1_000L);
        long checkpointInterval = context.configuration("checkpointIntervalMillis", Long.class, 25L);
        String label = context.configuration("label", String.class, "probe");
        if (duration < 0 || checkpointInterval < 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("durationMillis must be >= 0 and checkpointIntervalMillis must be > 0"));
        }
        logger.message(PluginLogLevel.DEBUG, "probe.started", fields(context, event, label));
        CompletableFuture<Void> result = new CompletableFuture<>();
        checkpoint(context, event, label, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(duration), checkpointInterval, result);
        return result;
    }

    private void checkpoint(ActionContext context, KuudraEvent event, String label, long deadline,
                            long intervalMillis, CompletableFuture<Void> result) {
        context.executionControl().checkpoint().whenComplete((decision, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            } else if (decision == ExecutionDecision.CANCEL) {
                logger.message(PluginLogLevel.INFO, "probe.cancelled", fields(context, event, label));
                result.complete(null);
            } else if (System.nanoTime() >= deadline) {
                logger.message(PluginLogLevel.INFO, "probe.completed", fields(context, event, label));
                result.complete(null);
            } else {
                ScheduledExecutorService current = scheduler;
                if (current == null || current.isShutdown()) {
                    result.completeExceptionally(new IllegalStateException("Session probe handler is destroyed"));
                } else {
                    current.schedule(() -> checkpoint(context, event, label, deadline, intervalMillis, result),
                            intervalMillis, TimeUnit.MILLISECONDS);
                }
            }
        });
    }

    private static Map<String, Object> fields(ActionContext context, KuudraEvent event, String label) {
        return Map.of("label", label, "flowId", context.flowId(), "sessionId", context.sessionId(),
                "eventId", event.id(), "eventType", event.type());
    }

    @Override public CompletionStage<Void> destroy() {
        ScheduledExecutorService current = scheduler;
        scheduler = null;
        if (current != null) current.shutdownNow();
        return CompletableFuture.completedFuture(null);
    }
}
