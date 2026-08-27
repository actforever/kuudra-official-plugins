package io.github.actforever.kuudra.interaction;

import io.github.actforever.kuudra.plugin.KuudraPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Type-only plugin shared by input capture and input simulation implementations. */
public final class UserInteractionSpecPlugin implements KuudraPlugin {
    @Override public String id() { return "user-interaction-spec"; }
    @Override public CompletionStage<Void> start() { return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> stop() { return CompletableFuture.completedFuture(null); }
}
