package io.github.actforever.kuudra.processcontrol;

import io.github.actforever.kuudra.api.context.*;
import io.github.actforever.kuudra.api.event.EventHandlerContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.api.session.CurrentSessionControl;
import io.github.actforever.kuudra.windowshost.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProcessControlEventHandlerTest {
    @Test void suspendRetainsHandlerCompletionUntilTheBrokerRestoresTheProcess() throws Exception {
        FakeLease lease = new FakeLease();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ProcessControlEventHandler handler = new ProcessControlEventHandler(lease, configuration(), scheduler);
        try {
            handler.start().toCompletableFuture().join();
            CompletableFuture<Void> handled = handler.suspend(KuudraEvent.of("test", Map.of()), context(ExecutionDecision.CONTINUE,
                    Map.of("target", "test", "durationMillis", 500L))).toCompletableFuture();
            assertFalse(handled.isDone());
            lease.complete(ProcessOperationOutcome.EXPIRED);
            handled.get(1, TimeUnit.SECONDS);
        } finally {
            handler.destroy().toCompletableFuture().join();
            scheduler.shutdownNow();
        }
    }

    @Test void cancellationExplicitlyRestoresTheOwnedOperation() throws Exception {
        FakeLease lease = new FakeLease();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ProcessControlEventHandler handler = new ProcessControlEventHandler(lease, configuration(), scheduler);
        try {
            handler.start().toCompletableFuture().join();
            handler.suspend(KuudraEvent.of("test", Map.of()), context(ExecutionDecision.CANCEL,
                    Map.of("target", "test", "durationMillis", 500L)))
                    .toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertEquals(1, lease.resumes.get());
        } finally {
            handler.destroy().toCompletableFuture().join();
            scheduler.shutdownNow();
        }
    }

    private static ProcessControlConfiguration configuration() {
        return new ProcessControlConfiguration(true, List.of(new ProcessTarget("test", Path.of("C:\\test.exe"))),
                500, 1_000);
    }

    private static EventHandlerContext context(ExecutionDecision decision, Map<String, Object> arguments) {
        ExecutionControl control = () -> decision;
        UUID sessionId = UUID.randomUUID(); StaticContext values = new StaticContext();
        return new EventHandlerContext() {
            @Override public UUID sessionId() { return sessionId; }
            @Override public String abilityId() { return "test/process"; }
            @Override public long abilityRevision() { return 1; }
            @Override public String nodeId() { return "suspend"; }
            @Override public String handlerName() { return "suspend"; }
            @Override public SessionContext session() { return values; }
            @Override public AbilityContext ability() { return values; }
            @Override public GlobalContext global() { return values; }
            @Override public TypedValueMap arguments() { return TypedValueMap.of(arguments); }
            @Override public ExecutionControl executionControl() { return control; }
            @Override public CurrentSessionControl sessionControl() { return CurrentSessionControl.unavailable(sessionId); }
            @Override public boolean emit(KuudraEvent event) { return true; }
        };
    }

    private static final class StaticContext implements SessionContext, AbilityContext, GlobalContext {
        @Override public Map<String, Object> snapshot() { return Map.of(); }
        @Override public boolean compareAndSet(Map<String, Object> expected, Map<String, Object> replacement) { return false; }
        @Override public Map<String, Object> update(java.util.function.UnaryOperator<Map<String, Object>> operation) { return Map.of(); }
    }

    private static final class FakeLease implements ProcessControlLease {
        private final UUID id = UUID.randomUUID();
        private final CompletableFuture<ProcessOperationResult> completion = new CompletableFuture<>();
        private final AtomicInteger resumes = new AtomicInteger();

        @Override public CompletionStage<ProcessOperation> suspend(String target, Long pid, Duration duration) {
            return CompletableFuture.completedFuture(new ProcessOperation(id, 42,
                    Instant.now().plus(duration), completion));
        }

        @Override public CompletionStage<Void> resume(String target, Long pid) {
            resumes.incrementAndGet();
            complete(ProcessOperationOutcome.EXPLICIT_RESUME);
            return CompletableFuture.completedFuture(null);
        }

        void complete(ProcessOperationOutcome outcome) {
            Instant now = Instant.now();
            completion.complete(new ProcessOperationResult(id, "test", 42, outcome, now.minusMillis(10), now));
        }

        @Override public CompletionStage<Void> restoreAll() {
            if (!completion.isDone()) complete(ProcessOperationOutcome.OWNER_RESTORED);
            return CompletableFuture.completedFuture(null);
        }
        @Override public void close() { }
    }
}
