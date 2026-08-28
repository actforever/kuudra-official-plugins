package io.github.actforever.kuudra.awtrobot;

import io.github.actforever.kuudra.plugin.KuudraPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class AwtRobotPlugin implements KuudraPlugin {
    @Override public String id() { return "awt-robot"; }
    @Override public CompletionStage<Void> start() { return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> stop() { SharedRobotDevice.INSTANCE.close(); return CompletableFuture.completedFuture(null); }
}
