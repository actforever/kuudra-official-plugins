package io.github.actforever.kuudra.jnativehook;

import io.github.actforever.kuudra.plugin.KuudraPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class JNativeHookPlugin implements KuudraPlugin {
    @Override public String id() { return "jnativehook"; }
    @Override public CompletionStage<Void> start() { return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> stop() { return CompletableFuture.completedFuture(null); }
}
