package io.github.actforever.kuudra.macro;

import io.github.actforever.kuudra.plugin.KuudraPlugin;

public final class MacroSpecPlugin implements KuudraPlugin {
    @Override public String id() { return "macro-spec"; }
    @Override public java.util.concurrent.CompletionStage<Void> start() { return java.util.concurrent.CompletableFuture.completedFuture(null); }
    @Override public java.util.concurrent.CompletionStage<Void> stop() { return java.util.concurrent.CompletableFuture.completedFuture(null); }
}
