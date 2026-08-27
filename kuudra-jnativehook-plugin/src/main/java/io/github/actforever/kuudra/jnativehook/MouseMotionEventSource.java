package io.github.actforever.kuudra.jnativehook;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseMotionListener;
import io.github.actforever.kuudra.interaction.*;
import io.github.actforever.kuudra.plugin.PluginComponentContext;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.EventEmission;
import io.github.actforever.kuudra.plugin.annotation.InstancePolicy;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.Map;
import java.util.concurrent.*;

@io.github.actforever.kuudra.plugin.annotation.EventSource(value = "jnativehook-mouse-motion",
        instancePolicy = @InstancePolicy(maxInstances = 1, exclusivityDomain = "actforever/jnativehook-mouse-motion", threadSafe = true))
@ComponentDoc(purpose = "Captures global mouse movement with configurable coalescing or throttling.",
        lifecyclePhases = {"initialize: validate output strategy", "start: create the sampler and attach the motion listener",
                "pause: detach listener and discard buffered motion", "resume: reattach from a clean sampling window",
                "stop: discard buffered motion, stop the sampler and release the native hook lease"},
        configuration = {
                @SpecProperty(path = "output.strategy", type = MotionOutputStrategy.class,
                        defaultValue = "\"COALESCE\"", allowedValues = {"COALESCE", "THROTTLE", "UNLIMITED"},
                        description = "COALESCE emits the leading and latest positions, THROTTLE emits only the leading position, and UNLIMITED emits every native event.",
                        examples = {"\"COALESCE\"", "\"UNLIMITED\""}),
                @SpecProperty(path = "output.intervalMillis", type = Long.class, defaultValue = "16",
                        description = "Sampling interval for COALESCE and THROTTLE; ignored by UNLIMITED.", examples = {"8", "16", "33"})
        },
        emittedEvents = {
                @EventEmission(stage = "native mouse moved", eventType = InteractionEvents.MOUSE_MOVED,
                        dataExample = "{\"user-interaction\":{\"position\":{\"x\":10,\"y\":20,\"coordinateSpace\":\"SCREEN\"},\"phase\":\"MOVED\"}}"),
                @EventEmission(stage = "native mouse dragged", eventType = InteractionEvents.MOUSE_DRAGGED)
        })
public final class MouseMotionEventSource extends AbstractNativeEventSource implements NativeMouseMotionListener {
    private final Object samplingMonitor = new Object();
    private MotionOutputOptions output = MotionOutputOptions.defaults();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> trailingTask;
    private NativeMouseEvent pending;
    private InteractionPhase pendingPhase;
    private long lastEmissionNanos;
    private final Runnable attachAction;
    private final Runnable detachAction;

    public MouseMotionEventSource() {
        super();
        this.attachAction = () -> GlobalScreen.addNativeMouseMotionListener(this);
        this.detachAction = () -> GlobalScreen.removeNativeMouseMotionListener(this);
    }

    MouseMotionEventSource(NativeHookController controller, MotionOutputOptions output,
                           Runnable attachAction, Runnable detachAction) {
        super(controller);
        this.output = output;
        this.attachAction = attachAction;
        this.detachAction = detachAction;
    }

    @Override public CompletionStage<Void> initialize(PluginComponentContext context) {
        output = context.configuration("output", MotionOutputOptions.class, MotionOutputOptions.defaults());
        return super.initialize(context);
    }

    @Override public CompletionStage<Void> start() {
        synchronized (samplingMonitor) {
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "kuudra-jnativehook-mouse-motion");
                    thread.setDaemon(true);
                    return thread;
                });
            }
        }
        CompletionStage<Void> result = super.start();
        result.whenComplete((ignored, error) -> { if (error != null) shutdownSampler(); });
        return result;
    }

    @Override public void nativeMouseMoved(NativeMouseEvent event) { accept(event, InteractionPhase.MOVED); }
    @Override public void nativeMouseDragged(NativeMouseEvent event) { accept(event, InteractionPhase.DRAGGED); }

    private void accept(NativeMouseEvent event, InteractionPhase phase) {
        if (!isEmitting()) return;
        if (output.strategy() == MotionOutputStrategy.UNLIMITED) {
            emit(event, phase);
            return;
        }
        synchronized (samplingMonitor) {
            long now = System.nanoTime();
            long intervalNanos = TimeUnit.MILLISECONDS.toNanos(output.intervalMillis());
            long elapsed = now - lastEmissionNanos;
            if (lastEmissionNanos == 0 || elapsed >= intervalNanos) {
                lastEmissionNanos = now;
                emit(event, phase);
                return;
            }
            if (output.strategy() == MotionOutputStrategy.THROTTLE) return;
            pending = event;
            pendingPhase = phase;
            if (trailingTask == null || trailingTask.isDone()) {
                long delay = Math.max(1, intervalNanos - elapsed);
                trailingTask = scheduler.schedule(this::emitTrailing, delay, TimeUnit.NANOSECONDS);
            }
        }
    }

    private void emitTrailing() {
        NativeMouseEvent event;
        InteractionPhase phase;
        synchronized (samplingMonitor) {
            event = pending;
            phase = pendingPhase;
            pending = null;
            pendingPhase = null;
            trailingTask = null;
            if (event != null) lastEmissionNanos = System.nanoTime();
        }
        if (event != null && isEmitting()) emit(event, phase);
    }

    private void emit(NativeMouseEvent event, InteractionPhase phase) {
        String type = phase == InteractionPhase.DRAGGED ? InteractionEvents.MOUSE_DRAGGED : InteractionEvents.MOUSE_MOVED;
        emitSafely(NativeKuudraEvents.event(type, Map.of(
                        InteractionEvents.POSITION, NativeEventMapper.position(event),
                        InteractionEvents.PHASE, phase,
                        InteractionEvents.MODIFIERS, NativeEventMapper.modifiers(event.getModifiers())), event,
                Map.of("x", event.getX(), "y", event.getY(), "button", event.getButton())));
    }

    @Override protected void onPaused() { resetWindow(); }
    @Override protected void onStopped() { shutdownSampler(); }

    private void resetWindow() {
        synchronized (samplingMonitor) {
            if (trailingTask != null) trailingTask.cancel(false);
            trailingTask = null;
            pending = null;
            pendingPhase = null;
            lastEmissionNanos = 0;
        }
    }

    private void shutdownSampler() {
        ScheduledExecutorService current;
        synchronized (samplingMonitor) {
            resetWindow();
            current = scheduler;
            scheduler = null;
        }
        if (current != null) current.shutdownNow();
    }

    @Override protected String componentName() { return "jnativehook-mouse-motion"; }
    @Override protected void attachListener() { attachAction.run(); }
    @Override protected void detachListener() { detachAction.run(); }
}
