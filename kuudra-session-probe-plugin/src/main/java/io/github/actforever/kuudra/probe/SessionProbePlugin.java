package io.github.actforever.kuudra.probe;

import io.github.actforever.kuudra.plugin.KuudraPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Diagnostic components used to verify scheduling and Session dependency behavior. */
public final class SessionProbePlugin implements KuudraPlugin {
    @Override public String id() { return "session-probe"; }
    @Override public CompletionStage<Void> start() { return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> stop() { return CompletableFuture.completedFuture(null); }
}
