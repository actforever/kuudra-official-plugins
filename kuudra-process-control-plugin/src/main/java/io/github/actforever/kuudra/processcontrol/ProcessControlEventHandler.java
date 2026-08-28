package io.github.actforever.kuudra.processcontrol;

import io.github.actforever.kuudra.api.KuudraException;
import io.github.actforever.kuudra.api.action.ActionContext;
import io.github.actforever.kuudra.api.component.EventHandler;
import io.github.actforever.kuudra.api.context.ExecutionDecision;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.*;
import io.github.actforever.kuudra.plugin.annotation.*;
import io.github.actforever.kuudra.windowshost.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@io.github.actforever.kuudra.plugin.annotation.EventHandler(value = "process-control",
        instancePolicy = @InstancePolicy(threadSafe = true))
@ComponentDoc(purpose = "Suspends or resumes an explicitly authorized Windows process through the privileged native host.",
        lifecyclePhases = {"initialize: validate the static target allowlist and acquire an elevated capability",
                "handle: execute SUSPEND or RESUME while retaining the Session lease until restoration",
                "stop/destroy: restore every process operation owned by this Component"},
        configuration = {
                @SpecProperty(path = "allowElevation", type = Boolean.class, required = true, defaultValue = "false",
                        description = "Explicitly permits this resource to trigger the one-time Windows UAC prompt."),
                @SpecProperty(path = "targets", type = Map.class, required = true,
                        description = "Static alias map. Each value requires an absolute executablePath and cannot contain placeholders.",
                        examples = {"{\"gta\":{\"executablePath\":\"C:\\\\Games\\\\GTA5.exe\"}}"}),
                @SpecProperty(path = "action", type = String.class, required = true, allowedValues = {"SUSPEND", "RESUME"},
                        description = "Invocation action; may be resolved from Event/Session/Flow/Global context."),
                @SpecProperty(path = "target", type = String.class, required = true,
                        description = "Authorized target alias; may be resolved from context."),
                @SpecProperty(path = "pid", type = Long.class,
                        description = "Optional PID. Required when more than one running process matches the target path."),
                @SpecProperty(path = "defaultDurationMillis", type = Long.class, defaultValue = "10000",
                        description = "SUSPEND duration used when durationMillis is absent."),
                @SpecProperty(path = "maxDurationMillis", type = Long.class, defaultValue = "60000",
                        description = "Trusted deployment ceiling for one suspension, up to 86400000 milliseconds."),
                @SpecProperty(path = "durationMillis", type = Long.class,
                        description = "Optional SUSPEND duration, bounded by maxDurationMillis; may be resolved from context.")
        })
public final class ProcessControlEventHandler implements EventHandler, PluginComponentLifecycle {
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

    @Override public CompletionStage<Void> initialize(PluginComponentContext context) {
        try {
            configuration = ProcessControlConfiguration.decode(context.configuration());
            logger = context.logger();
            scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "kuudra-process-control");
                thread.setDaemon(true);
                return thread;
            });
            lease = WindowsNativeHost.acquireProcessControl(context.componentReference(), configuration.allowElevation(),
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

    @Override public CompletionStage<Void> handle(KuudraEvent event, ActionContext context) {
        if (!started.get()) return CompletableFuture.failedFuture(new KuudraException("process-control is not running"));
        try {
            String action = context.configuration("action", String.class).trim().replace('-', '_').toUpperCase(Locale.ROOT);
            String target = context.configuration("target", String.class);
            Long pid = context.configuration().containsKey("pid") ? context.configuration("pid", Long.class) : null;
            CompletionStage<Void> result = switch (action) {
                case "SUSPEND" -> suspend(context, target, pid);
                case "RESUME" -> lease.resume(target, pid);
                default -> CompletableFuture.failedFuture(new IllegalArgumentException("Unsupported process-control action: " + action));
            };
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

    private CompletionStage<Void> suspend(ActionContext context, String target, Long pid) {
        long duration = context.configuration().containsKey("durationMillis")
                ? context.configuration("durationMillis", Long.class) : configuration.defaultDurationMillis();
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

    private void monitor(ActionContext context, String target, Long pid, ProcessOperation operation,
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
