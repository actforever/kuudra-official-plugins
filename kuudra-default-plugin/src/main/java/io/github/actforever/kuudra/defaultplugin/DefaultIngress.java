package io.github.actforever.kuudra.defaultplugin;
import io.github.actforever.kuudra.api.component.IngressDecision;
import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.annotation.*;
@Ingress("default-ingress")
@ComponentDoc(purpose="Unconditionally admits every incoming Event into the session domain and selects its session group.",
        usageExample="groupKey: '${event#deviceId}'\npolicy: SERIAL\ngroupScope: FLOW_BINDING",
        configuration={
                @SpecProperty(path="groupKey", type=String.class, description="Session group key; defaults to the Event type.", examples={"\"keyboard\"", "\"device-1\""}),
                @SpecProperty(path="policy", type=String.class, description="Session scheduling policy.", examples={"\"PARALLEL\"", "\"SERIAL\""}, allowedValues={"PARALLEL","SERIAL","IGNORE","CANCEL_AND_REPLACE_PENDING","CANCEL_AND_KEEP_PENDING","TOGGLE"}),
                @SpecProperty(path="groupScope", type=String.class, description="Isolation scope of a session group.", examples={"\"FLOW_BINDING\""}, allowedValues={"FLOW_BINDING","INGRESS"}),
                @SpecProperty(path="maxParallelSessions", type=Integer.class, description="Maximum concurrent sessions per group.", examples={"1","64"}),
                @SpecProperty(path="queueCapacity", type=Integer.class, description="Maximum queued Events per group.", examples={"32","256"})
        })
public final class DefaultIngress implements io.github.actforever.kuudra.api.component.Ingress {
    @Override public IngressDecision admit(KuudraEvent event, EventContext context) {
        return IngressDecision.accept(context.configuration("groupKey", String.class, event.type()), event);
    }
}
