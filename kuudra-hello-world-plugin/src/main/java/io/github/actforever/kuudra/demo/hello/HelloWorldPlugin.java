package io.github.actforever.kuudra.demo.hello;

import io.github.actforever.kuudra.plugin.KuudraPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Minimal plugin entrypoint. Components are discovered from annotations in this archive. */
public final class HelloWorldPlugin implements KuudraPlugin {
    @Override public String id() { return "hello-world"; }
    @Override public CompletionStage<Void> start() { return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> stop() { return CompletableFuture.completedFuture(null); }
}
