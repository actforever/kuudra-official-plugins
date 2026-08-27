package io.github.actforever.kuudra.defaultplugin;

import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.EventData;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.List;
import java.util.Map;

@io.github.actforever.kuudra.plugin.annotation.EventAdapter("event-mapper")
@ComponentDoc(purpose = "Retypes an Event and projects configured values into namespaced Event data.", configuration = {
        @SpecProperty(path="outputType", type=String.class, description="Optional output Event type.", examples={"\"demo.normalized\""}),
        @SpecProperty(path="preserveData", type=Boolean.class, description="Whether existing Event data is retained.", defaultValue="true", examples={"true"}),
        @SpecProperty(path="data", type=Map.class, description="Namespace/field/value tree. Values may contain placeholders.", examples={"{normalized: {message: '${event#source.message}'}}"})
})
public final class EventMapper implements io.github.actforever.kuudra.api.component.EventAdapter {
    @Override public List<KuudraEvent> adapt(KuudraEvent event, EventContext context) {
        String type = context.configuration("outputType", String.class, event.type());
        boolean preserve = context.configuration("preserveData", Boolean.class, true);
        Map<?, ?> configured = context.configuration("data", Map.class, Map.of());
        EventData data = preserve ? event.data() : EventData.empty();
        for (Map.Entry<?, ?> namespace : configured.entrySet()) {
            if (!(namespace.getKey() instanceof String ns) || !(namespace.getValue() instanceof Map<?, ?> fields))
                throw new IllegalArgumentException("data must be a namespace-to-fields map");
            for (Map.Entry<?, ?> field : fields.entrySet()) {
                if (!(field.getKey() instanceof String key)) throw new IllegalArgumentException("Event field names must be strings");
                data = data.with(ns, key, field.getValue());
            }
        }
        return List.of(event.retype(type).withData(data));
    }
}
