package io.github.actforever.kuudra.defaultplugin;
import io.github.actforever.kuudra.api.component.IngressDecision;
import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.annotation.*;
@Ingress("plain-ingress")
@ComponentDoc(purpose="Unconditionally admits every incoming Event into the session domain and selects its session group.",
        configuration={
                @SpecProperty(path="groupKey", type=String.class, description="Session group key; defaults to the Event type.", examples={"\"keyboard\"", "\"device-1\""}),
                @SpecProperty(path="sessionLabels", type=java.util.Map.class, description="String labels used for automatic SessionCoordinationPolicy selection.", examples={"{\"role\":\"window\"}"})
        })
public final class PlainIngress implements io.github.actforever.kuudra.api.component.Ingress {
    @Override public IngressDecision admit(KuudraEvent event, EventContext context) {
        Object configured = context.configuration().get("sessionLabels");
        java.util.Map<String, String> labels = configured == null ? java.util.Map.of() : labels(configured);
        return IngressDecision.accept(context.configuration("groupKey", String.class, event.type()), event, java.util.Map.of(), labels);
    }

    private static java.util.Map<String, String> labels(Object value) {
        if (!(value instanceof java.util.Map<?, ?> map)) throw new IllegalArgumentException("sessionLabels must be an object");
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), String.valueOf(item)));
        return java.util.Map.copyOf(result);
    }
}
