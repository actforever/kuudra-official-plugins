package io.github.actforever.kuudra.awtrobot;

import io.github.actforever.kuudra.api.KuudraException;
import io.github.actforever.kuudra.api.action.ActionContext;
import io.github.actforever.kuudra.api.component.EventHandler;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.api.lifecycle.PausableLifecycle;
import io.github.actforever.kuudra.plugin.*;
import io.github.actforever.kuudra.plugin.annotation.*;
import io.github.actforever.kuudra.macro.*;

import java.nio.file.*;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@io.github.actforever.kuudra.plugin.annotation.EventHandler(value = "awt-robot",
        instancePolicy = @InstancePolicy(threadSafe = false))
@ComponentDoc(purpose = "Executes a user-defined, cooperatively cancellable keyboard and mouse macro through AWT Robot.",
        lifecyclePhases = {"initialize: bind the plugin logger", "start: initialize the shared physical Robot device",
                "pause: Runtime checkpoints release held input and preserve logical progress",
                "resume: checkpoints restore logically held input", "stop/destroy: reject work and release input"},
        configuration = {
                @SpecProperty(path = "steps[]", type = Object[].class,
                        description = "Ordered macro steps. Supported actions include keyboard, mouse, control flow, Session cancellation and Event emission.",
                        examples = {"[{\"action\":\"keyTap\",\"key\":{\"code\":\"F24\",\"location\":\"STANDARD\"}}]",
                                "[{\"action\":\"keyPress\",\"key\":\"${event#user.key}\"},{\"action\":\"sleep\",\"durationMillis\":100},{\"action\":\"keyRelease\",\"key\":\"${event#user.key}\"}]"}),
                @SpecProperty(path = "script", type = String.class,
                        description = "Macro source relative to this plugin home. Exactly one of steps or script is required. The extension selects a separately installed language frontend.",
                        examples = {"\"macros/hello.kt\""}),
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
    private Map<String, Object> componentConfiguration = Map.of();
    private Path pluginHome;
    private Path script;
    private volatile MacroProgramDefinition compiledScript;
    private volatile ScriptFingerprint scriptFingerprint;

    public AwtRobotEventHandler() { this(SharedRobotDevice.INSTANCE); }
    AwtRobotEventHandler(RobotDevice device) { this.device = java.util.Objects.requireNonNull(device); }

    @Override public CompletionStage<Void> initialize(PluginComponentContext context) {
        logger = context.logger();
        componentConfiguration = context.configuration();
        pluginHome = context.plugin().home();
        try {
            boolean hasSteps = componentConfiguration.containsKey("steps");
            boolean hasScript = componentConfiguration.containsKey("script");
            if (hasSteps == hasScript) throw new KuudraException("Exactly one of steps or script must be configured");
            if (hasScript) { script = resolveScript(String.valueOf(componentConfiguration.get("script"))); compileScript(true); }
            else MacroCodec.decode(componentConfiguration);
            return CompletableFuture.completedFuture(null);
        } catch (RuntimeException error) { return CompletableFuture.failedFuture(KuudraException.wrap("Invalid AWT Robot macro configuration", error)); }
    }

    @Override public CompletionStage<Void> start() {
        try { if (script != null) compileScript(false); device.driver(); started.set(true); return CompletableFuture.completedFuture(null); }
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
        try { program = new MacroProgram(script == null ? MacroCodec.decode(context.configuration()) : compiledScript); }
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

    private Path resolveScript(String configured) {
        Path relative;
        try { relative = Path.of(configured); } catch (InvalidPathException error) { throw new KuudraException("Invalid macro script path: " + configured, error); }
        if (relative.isAbsolute()) throw new KuudraException("Macro script must be relative to plugin home: " + configured);
        try {
            Path home = pluginHome.toRealPath();
            Path resolved = home.resolve(relative).normalize().toRealPath();
            if (!resolved.startsWith(home)) throw new KuudraException("Macro script escapes plugin home: " + configured);
            if (!Files.isRegularFile(resolved)) throw new KuudraException("Macro script is not a regular file: " + configured);
            return resolved;
        } catch (IOException error) { throw new KuudraException("Cannot resolve macro script under plugin home: " + configured, error); }
    }

    private synchronized void compileScript(boolean force) {
        ScriptFingerprint current = fingerprint(script);
        if (!force && current.equals(scriptFingerprint)) return;
        String filename = script.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        if (dot < 0) throw new KuudraException("Macro script has no language extension: " + filename);
        String extension = filename.substring(dot).toLowerCase(java.util.Locale.ROOT);
        MacroFrontend frontend = MacroFrontendRegistry.find(extension)
                .orElseThrow(() -> new KuudraException("No macro frontend is registered for " + extension));
        long max = number(componentConfiguration.getOrDefault("maxTotalSteps", 10_000), "maxTotalSteps");
        long marker = number(componentConfiguration.getOrDefault("syntheticMarkerLifetimeMillis", 500), "syntheticMarkerLifetimeMillis");
        compiledScript = frontend.compile(script, new MacroCompileOptions(max, marker));
        scriptFingerprint = current;
    }

    private static ScriptFingerprint fingerprint(Path script) {
        try { return new ScriptFingerprint(Files.size(script), Files.getLastModifiedTime(script).toMillis()); }
        catch (IOException error) { throw new KuudraException("Cannot inspect macro script: " + script, error); }
    }
    private static long number(Object value, String path) {
        if (!(value instanceof Number number)) throw new KuudraException(path + " must be a number");
        return number.longValue();
    }
    private record ScriptFingerprint(long size, long modifiedMillis) { }
}
