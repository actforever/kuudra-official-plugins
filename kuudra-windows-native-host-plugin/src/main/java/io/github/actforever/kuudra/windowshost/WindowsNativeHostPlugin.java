package io.github.actforever.kuudra.windowshost;

import io.github.actforever.kuudra.plugin.KuudraPlugin;
import io.github.actforever.kuudra.plugin.PluginContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class WindowsNativeHostPlugin implements KuudraPlugin {
    private NativeHostProvider provider;

    @Override public String id() { return "windows-native-host"; }

    @Override public CompletionStage<Void> initialize(PluginContext context) {
        provider = new NativeHostProvider(context.home(), context.logger());
        WindowsNativeHost.install(provider);
        context.resources().register("windows-native-host", provider);
        return CompletableFuture.completedFuture(null);
    }

    @Override public CompletionStage<Void> start() { return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> stop() { return CompletableFuture.completedFuture(null); }

    @Override public CompletionStage<Void> destroy() {
        NativeHostProvider current = provider;
        provider = null;
        if (current != null) {
            WindowsNativeHost.uninstall(current);
            current.close();
        }
        return CompletableFuture.completedFuture(null);
    }
}
