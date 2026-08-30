package io.github.actforever.kuudra.defaultplugin;
import io.github.actforever.kuudra.api.event.EventHandlerContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.*;
import io.github.actforever.kuudra.plugin.annotation.ResourceDoc;
import java.util.Locale;
import java.util.concurrent.*;
@io.github.actforever.kuudra.plugin.annotation.Controller("system-control")
@ResourceDoc(purpose="Converts a routed event into a kernel or current-session control request.", lifecyclePhases={"initialize","handle","destroy"})
public final class SystemControlEventHandler implements ResourceLifecycle {
    private PluginRuntimeServices runtime;
    @Override public CompletionStage<Void> initialize(ResourceContext context) { runtime=context.plugin().runtime(); return CompletableFuture.completedFuture(null); }
    @io.github.actforever.kuudra.plugin.annotation.EventHandler("control")
    public CompletionStage<Void> handle(KuudraEvent event, EventHandlerContext context) {
        if (runtime==null) return CompletableFuture.failedFuture(new IllegalStateException("System control handler is not initialized"));
        String action=context.arguments().get("action", String.class);
        return runtime.control(KernelControlAction.valueOf(action.replace('-','_').toUpperCase(Locale.ROOT)), context.sessionId());
    }
}
