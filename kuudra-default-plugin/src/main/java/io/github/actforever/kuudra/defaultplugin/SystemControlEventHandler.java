package io.github.actforever.kuudra.defaultplugin;
import io.github.actforever.kuudra.api.action.ActionContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.*;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import java.util.Locale;
import java.util.concurrent.*;
@io.github.actforever.kuudra.plugin.annotation.EventHandler("system-control")
@ComponentDoc(purpose="Converts a routed event into a kernel or current-session control request.", usageExample="action: PAUSE_KERNEL", lifecyclePhases={"initialize","handle","destroy"})
public final class SystemControlEventHandler implements io.github.actforever.kuudra.api.component.EventHandler, PluginComponentLifecycle {
    private PluginRuntimeServices runtime;
    @Override public CompletionStage<Void> initialize(PluginComponentContext context) { runtime=context.plugin().runtime(); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> handle(KuudraEvent event, ActionContext context) {
        if (runtime==null) return CompletableFuture.failedFuture(new IllegalStateException("System control handler is not initialized"));
        String action=context.configuration("action", String.class);
        return runtime.control(KernelControlAction.valueOf(action.replace('-','_').toUpperCase(Locale.ROOT)), context.sessionId());
    }
}
