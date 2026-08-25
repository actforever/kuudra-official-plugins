package io.github.actforever.kuudra.defaultplugin;
import io.github.actforever.kuudra.api.component.IngressDecision;
import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.annotation.*;
@Ingress("default")
@ComponentDoc(purpose="Admits an event into the session domain and selects its session group.", usageExample="groupKey: '${event#deviceId}'")
public final class DefaultIngress implements io.github.actforever.kuudra.api.component.Ingress {
    @Override public IngressDecision admit(KuudraEvent event, EventContext context) {
        return IngressDecision.accept(context.configuration("groupKey", String.class, event.type()), event);
    }
}
