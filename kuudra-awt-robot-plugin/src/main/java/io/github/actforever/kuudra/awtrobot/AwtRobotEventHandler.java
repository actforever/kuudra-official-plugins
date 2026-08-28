package io.github.actforever.kuudra.awtrobot;

import io.github.actforever.kuudra.api.KuudraException;
import io.github.actforever.kuudra.api.action.ActionContext;
import io.github.actforever.kuudra.api.component.EventHandler;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.api.lifecycle.PausableLifecycle;
import io.github.actforever.kuudra.plugin.*;
import io.github.actforever.kuudra.plugin.annotation.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@io.github.actforever.kuudra.plugin.annotation.EventHandler(value = "awt-robot",
        instancePolicy = @InstancePolicy(threadSafe = false))
@ComponentDoc(purpose = "Executes a user-defined, cooperatively cancellable keyboard and mouse macro through AWT Robot.",
        lifecyclePhases = {"initialize: bind the plugin logger", "start: initialize the shared physical Robot device",
                "pause: Runtime checkpoints release held input and preserve logical progress",
                "resume: checkpoints restore logically held input", "stop/destroy: reject work and release input"},
        configuration = {
                @SpecProperty(path = "steps[]", type = Object[].class, required = true,
                        description = "Ordered macro steps. Supported actions include keyboard, mouse, control flow, Session cancellation and Event emission.",
                        examples = {"[{\"action\":\"keyTap\",\"key\":{\"code\":\"F24\",\"location\":\"STANDARD\"}}]",
                                "[{\"action\":\"keyPress\",\"key\":\"${event#user.key}\"},{\"action\":\"sleep\",\"durationMillis\":100},{\"action\":\"keyRelease\",\"key\":\"${event#user.key}\"}]"}),
                @SpecProperty(path = "maxTotalSteps", type = Long.class, defaultValue = "10000",
                        description = "Maximum executed steps per invocation, including nested control-flow steps.", examples = {"1000", "10000"}),
                @SpecProperty(path = "syntheticMarkerLifetimeMillis", type = Long.class, defaultValue = "500",
                        description = "Best-effort correlation lifetime used to prevent recaptured Robot input from feeding back into Flows.", examples = {"250", "500"})
        })
public final class AwtRobotEventHandler implements EventHandler, PausableLifecycle, PluginComponentLifecycle {
    private final RobotDevice device;
    private final AtomicBoolean started = new AtomicBoolean();
    private final java.util.Set<CompletableFuture<Void>> inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private PluginLogger logger;

    public AwtRobotEventHandler() { this(SharedRobotDevice.INSTANCE); }
    AwtRobotEventHandler(RobotDevice device) { this.device = java.util.Objects.requireNonNull(device); }

    @Override public CompletionStage<Void> initialize(PluginComponentContext context) {
        logger = context.logger();
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<Void> start() {
        try { device.driver(); started.set(true); return CompletableFuture.completedFuture(null); }
        catch (RuntimeException error) { return CompletableFuture.failedFuture(KuudraException.wrap("Failed to start AWT Robot handler", error)); }
    }

    @Override public CompletionStage<Void> pause() { return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> resume() { return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> stop() {
        started.set(false);
        return CompletableFuture.allOf(inFlight.toArray(CompletableFuture[]::new));
    }

    @Override public CompletionStage<Void> handle(KuudraEvent event, ActionContext context) {
        if (!started.get()) return CompletableFuture.failedFuture(new KuudraException("AWT Robot handler is not running"));
        final MacroProgram program;
        try { program = MacroProgram.parse(context.configuration()); }
        catch (RuntimeException error) {
            KuudraException failure = KuudraException.wrap("Invalid AWT Robot macro configuration", error);
            if (logger != null) logger.error("awt-robot.macro.configuration-invalid", failure);
            return CompletableFuture.failedFuture(failure);
        }
        CompletableFuture<Void> future = device.submit(() -> {
            if (!started.get()) throw new KuudraException("AWT Robot handler stopped before macro execution");
            program.execute(event, context, device.driver());
        }).toCompletableFuture();
        inFlight.add(future);
        future.whenComplete((ignored, error) -> {
            inFlight.remove(future);
            if (error != null && logger != null) {
                logger.error("awt-robot.macro.failed", unwrap(error));
            }
        });
        return future;
    }

    private static Throwable unwrap(Throwable error) {
        if ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    @Override public CompletionStage<Void> destroy() { return stop(); }
}
