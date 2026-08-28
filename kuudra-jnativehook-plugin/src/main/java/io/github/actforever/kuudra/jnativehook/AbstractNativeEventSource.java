package io.github.actforever.kuudra.jnativehook;

import io.github.actforever.kuudra.api.KuudraException;
import io.github.actforever.kuudra.api.component.EventSource;
import io.github.actforever.kuudra.api.event.EventEmitter;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.api.lifecycle.PausableLifecycle;
import io.github.actforever.kuudra.plugin.PluginComponentContext;
import io.github.actforever.kuudra.plugin.PluginComponentLifecycle;
import io.github.actforever.kuudra.plugin.PluginLogger;
import io.github.actforever.kuudra.interaction.InjectedInteractionRegistry;
import io.github.actforever.kuudra.interaction.InteractionEvents;
import io.github.actforever.kuudra.interaction.InteractionSignature;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

abstract class AbstractNativeEventSource implements EventSource, PausableLifecycle, PluginComponentLifecycle {
    private final Object lifecycleMonitor = new Object();
    private final NativeHookController hookController;
    private volatile EventEmitter emitter;
    private volatile PluginLogger logger;
    private boolean started;
    private boolean attached;
    private SyntheticEventPolicy syntheticEventPolicy = SyntheticEventPolicy.DROP;

    AbstractNativeEventSource() { this(SharedNativeHookController.INSTANCE); }
    AbstractNativeEventSource(NativeHookController hookController) { this.hookController = Objects.requireNonNull(hookController); }

    @Override public CompletionStage<Void> initialize(PluginComponentContext context) {
        logger = context.logger();
        String policy = context.configuration("syntheticEventPolicy", String.class, SyntheticEventPolicy.DROP.name());
        try { syntheticEventPolicy = SyntheticEventPolicy.valueOf(policy.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException error) { return CompletableFuture.failedFuture(new KuudraException("Unsupported syntheticEventPolicy: " + policy, error)); }
        return CompletableFuture.completedFuture(null);
    }

    @Override public void setEmitter(EventEmitter emitter) { this.emitter = Objects.requireNonNull(emitter, "emitter"); }

    @Override public CompletionStage<Void> start() {
        synchronized (lifecycleMonitor) {
            if (started) return CompletableFuture.completedFuture(null);
            if (emitter == null) return CompletableFuture.failedFuture(new KuudraException(componentName() + " emitter is not configured"));
            try {
                hookController.acquire();
                attachListener();
                attached = true;
                started = true;
                return CompletableFuture.completedFuture(null);
            } catch (Exception error) {
                try { hookController.release(); } catch (Exception releaseError) { error.addSuppressed(releaseError); }
                return CompletableFuture.failedFuture(KuudraException.wrap("Failed to start " + componentName(), error));
            }
        }
    }

    @Override public CompletionStage<Void> pause() {
        synchronized (lifecycleMonitor) {
            if (!started || !attached) return CompletableFuture.completedFuture(null);
            try {
                detachListener();
                attached = false;
                onPaused();
                return CompletableFuture.completedFuture(null);
            } catch (Exception error) {
                return CompletableFuture.failedFuture(KuudraException.wrap("Failed to pause " + componentName(), error));
            }
        }
    }

    @Override public CompletionStage<Void> resume() {
        synchronized (lifecycleMonitor) {
            if (!started || attached) return CompletableFuture.completedFuture(null);
            try {
                attachListener();
                attached = true;
                return CompletableFuture.completedFuture(null);
            } catch (Exception error) {
                return CompletableFuture.failedFuture(KuudraException.wrap("Failed to resume " + componentName(), error));
            }
        }
    }

    @Override public CompletionStage<Void> stop() {
        synchronized (lifecycleMonitor) {
            if (!started) return CompletableFuture.completedFuture(null);
            Exception failure = null;
            if (attached) {
                try { detachListener(); } catch (Exception error) { failure = error; }
            }
            attached = false;
            onStopped();
            try { hookController.release(); } catch (Exception error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
            started = false;
            return failure == null ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.failedFuture(KuudraException.wrap("Failed to stop " + componentName(), failure));
        }
    }

    protected final void emitSafely(KuudraEvent event, InteractionSignature signature) {
        if (!isEmitting()) return;
        boolean synthetic = InjectedInteractionRegistry.global().consume(signature);
        if (synthetic && syntheticEventPolicy == SyntheticEventPolicy.DROP) return;
        KuudraEvent output = event.withData(event.data().with(InteractionEvents.DATA_NAMESPACE,
                InteractionEvents.SYNTHETIC, synthetic));
        try { emitter.emit(output); }
        catch (RuntimeException error) {
            PluginLogger current = logger;
            if (current != null) current.error("jnativehook.event.emit.failed", error);
        }
    }

    protected final boolean isEmitting() { synchronized (lifecycleMonitor) { return started && attached; } }
    protected void onPaused() { }
    protected void onStopped() { }
    protected abstract String componentName();
    protected abstract void attachListener();
    protected abstract void detachListener();
}
