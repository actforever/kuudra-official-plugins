package io.github.actforever.kuudra.processcontrol;

import io.github.actforever.kuudra.api.action.ActionContext;
import io.github.actforever.kuudra.api.context.*;
import io.github.actforever.kuudra.api.event.KuudraEvent;
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
            CompletableFuture<Void> handled = handler.handle(KuudraEvent.of("test", Map.of()), context(ExecutionDecision.CONTINUE,
                    Map.of("action", "SUSPEND", "target", "test", "durationMillis", 500L))).toCompletableFuture();
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
            handler.handle(KuudraEvent.of("test", Map.of()), context(ExecutionDecision.CANCEL,
                    Map.of("action", "SUSPEND", "target", "test", "durationMillis", 500L)))
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

    private static ActionContext context(ExecutionDecision decision, Map<String, Object> configuration) {
        ExecutionControl control = () -> decision;
        return new ActionContext(UUID.randomUUID(), "test", Map.of(), null, control, ignored -> true,
                Map.of(), configuration);
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
