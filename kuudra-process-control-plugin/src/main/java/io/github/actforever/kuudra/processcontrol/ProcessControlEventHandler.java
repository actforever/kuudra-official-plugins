package io.github.actforever.kuudra.processcontrol;

import io.github.actforever.kuudra.api.KuudraException;
import io.github.actforever.kuudra.api.context.ExecutionDecision;
import io.github.actforever.kuudra.api.event.EventHandlerContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.*;
import io.github.actforever.kuudra.plugin.annotation.*;
import io.github.actforever.kuudra.windowshost.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Controller(value = "process-controller", policy = @io.github.actforever.kuudra.plugin.annotation.ResourcePolicy(allowParallel = true))
@ResourceDoc(purpose = "Suspends or resumes an explicitly authorized Windows process through the privileged native host.",
        lifecyclePhases = {"initialize: validate the static target allowlist and acquire an elevated capability",
                "handle: execute SUSPEND or RESUME while retaining the Session lease until restoration",
                "stop/destroy: restore every process operation owned by this Component"},
        options = {
                @SpecProperty(path = "allowElevation", type = Boolean.class, required = true, defaultValue = "false",
                        description = "Explicitly permits this resource to trigger the one-time Windows UAC prompt."),
                @SpecProperty(path = "targets", type = Map.class, required = true,
                        description = "Static alias map. Each value requires an absolute executablePath and cannot contain placeholders.",
                        examples = {"{\"gta\":{\"executablePath\":\"C:\\\\Games\\\\GTA5.exe\"}}"}),
                @SpecProperty(path = "defaultDurationMillis", type = Long.class, defaultValue = "10000",
                        description = "SUSPEND duration used when durationMillis is absent."),
                @SpecProperty(path = "maxDurationMillis", type = Long.class, defaultValue = "60000",
                        description = "Trusted deployment ceiling for one suspension, up to 86400000 milliseconds.")
        }, arguments = {
                @SpecProperty(path = "target", type = String.class, required = true,
                        description = "Authorized target alias; may be resolved from context."),
                @SpecProperty(path = "pid", type = Long.class,
                        description = "Optional PID. Required when more than one running process matches the target path."),
                @SpecProperty(path = "durationMillis", type = Long.class,
                        description = "Optional SUSPEND duration, bounded by maxDurationMillis; may be resolved from context.")
        })
public final class ProcessControlEventHandler implements ResourceLifecycle {
    private final AtomicBoolean started = new AtomicBoolean();
    private final Set<CompletableFuture<Void>> inFlight = ConcurrentHashMap.newKeySet();
    private volatile ScheduledExecutorService scheduler;
    private volatile ProcessControlLease lease;
    private volatile ProcessControlConfiguration configuration;
    private volatile PluginLogger logger;

    public ProcessControlEventHandler() { }

    ProcessControlEventHandler(ProcessControlLease lease, ProcessControlConfiguration configuration,
                               ScheduledExecutorService scheduler) {
        this.lease = lease;
        this.configuration = configuration;
        this.scheduler = scheduler;
    }

    @Override public CompletionStage<Void> initialize(ResourceContext context) {
        try {
            configuration = ProcessControlConfiguration.decode(context.options());
            logger = context.logger();
            scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "kuudra-process-control");
                thread.setDaemon(true);
                return thread;
            });
            lease = WindowsNativeHost.acquireProcessControl(context.resourceReference(), configuration.allowElevation(),
                    configuration.targets(), configuration.maxDurationMillis());
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(KuudraException.wrap("Failed to initialize process-control", error));
        }
    }

    @Override public CompletionStage<Void> start() {
        started.set(true);
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<Void> stop() {
        started.set(false);
        ProcessControlLease current = lease;
        CompletionStage<Void> restored = current == null ? CompletableFuture.completedFuture(null) : current.restoreAll();
        return restored.thenCompose(ignored -> CompletableFuture.allOf(inFlight.toArray(CompletableFuture[]::new)));
    }

    @io.github.actforever.kuudra.plugin.annotation.EventHandler("suspend")
    public CompletionStage<Void> suspend(KuudraEvent event, EventHandlerContext context) {
        return invoke(context, true);
    }

    @io.github.actforever.kuudra.plugin.annotation.EventHandler("resume")
    public CompletionStage<Void> resume(KuudraEvent event, EventHandlerContext context) {
        return invoke(context, false);
    }

    private CompletionStage<Void> invoke(EventHandlerContext context, boolean suspend) {
        if (!started.get()) return CompletableFuture.failedFuture(new KuudraException("process-control is not running"));
        try {
            String target = context.arguments().get("target", String.class);
            Long pid = context.arguments().contains("pid") ? context.arguments().get("pid", Long.class) : null;
            CompletionStage<Void> result = suspend ? suspendForDuration(context, target, pid) : lease.resume(target, pid);
            CompletableFuture<Void> future = result.toCompletableFuture();
            inFlight.add(future);
            future.whenComplete((ignored, error) -> {
                inFlight.remove(future);
                if (error != null && logger != null) logger.error("process-control.operation.failed", unwrap(error));
            });
            return future;
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(KuudraException.wrap("Invalid process-control invocation", error));
        }
    }

    private CompletionStage<Void> suspendForDuration(EventHandlerContext context, String target, Long pid) {
        long duration = context.arguments().contains("durationMillis")
                ? context.arguments().get("durationMillis", Long.class) : configuration.defaultDurationMillis();
        if (duration < 100 || duration > configuration.maxDurationMillis()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("durationMillis must be between 100 and "
                    + configuration.maxDurationMillis()));
        }
        return lease.suspend(target, pid, Duration.ofMillis(duration)).thenCompose(operation -> {
            CompletableFuture<Void> completed = new CompletableFuture<>();
            monitor(context, target, pid, operation, completed);
            return completed;
        });
    }

    private void monitor(EventHandlerContext context, String target, Long pid, ProcessOperation operation,
                         CompletableFuture<Void> result) {
        if (result.isDone()) return;
        CompletableFuture<ProcessOperationResult> completion = operation.completion().toCompletableFuture();
        if (completion.isDone()) {
            completion.whenComplete((ignored, error) -> complete(result, error));
            return;
        }
        ExecutionDecision decision = context.executionControl().poll();
        if (decision == ExecutionDecision.CANCEL || !started.get()) {
            lease.resume(target, pid).whenComplete((ignored, error) -> {
                if (error != null) result.completeExceptionally(error);
                else completion.whenComplete((outcome, completionError) -> complete(result, completionError));
            });
            return;
        }
        if (decision == ExecutionDecision.PAUSE) {
            context.executionControl().checkpoint().whenComplete((afterPause, error) -> {
                if (error != null) result.completeExceptionally(error);
                else monitor(context, target, pid, operation, result);
            });
            return;
        }
        ScheduledExecutorService current = scheduler;
        if (current == null || current.isShutdown()) {
            result.completeExceptionally(new KuudraException("process-control scheduler is stopped"));
        } else {
            current.schedule(() -> monitor(context, target, pid, operation, result), 25, TimeUnit.MILLISECONDS);
        }
    }

    private static void complete(CompletableFuture<Void> result, Throwable error) {
        if (error == null) result.complete(null); else result.completeExceptionally(unwrap(error));
    }

    private static Throwable unwrap(Throwable error) {
        if ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) return error.getCause();
        return error;
    }

    @Override public CompletionStage<Void> destroy() {
        return stop().handle((ignored, error) -> {
            ProcessControlLease currentLease = lease;
            lease = null;
            if (currentLease != null) currentLease.close();
            ScheduledExecutorService currentScheduler = scheduler;
            scheduler = null;
            if (currentScheduler != null) currentScheduler.shutdownNow();
            if (error != null) throw new CompletionException(error);
            return null;
        });
    }
}
