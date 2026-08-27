package io.github.actforever.kuudra.defaultplugin;

import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.List;

@io.github.actforever.kuudra.plugin.annotation.EventInterpreter("any-order-event")
@ComponentDoc(purpose="Recognizes required Events in any order inside a bounded time window.", configuration={
        @SpecProperty(path="outputType", type=String.class, required=true, description="Type emitted after a complete match.", examples={"\"gesture.chord\""}),
        @SpecProperty(path="timeoutMs", type=Long.class, description="Window timeout in milliseconds.", defaultValue="3000", examples={"1000"}),
        @SpecProperty(path="requirements[]", type=java.util.Map.class, required=true, description="Selector/count requirements.", examples={"[{selector: {type: key.a}, count: 1}, {selector: {type: key.b}, count: 1}]"}),
        @SpecProperty(path="forbidden[]", type=java.util.Map.class, description="Selectors that reset progress.", examples={"[{type: key.escape}]"}),
        @SpecProperty(path="includeMatchedEvents", type=Boolean.class, defaultValue="true", description="Includes immutable matched Event snapshots.", examples={"true"})
})
public final class AnyOrderEventInterpreter extends AbstractWindowInterpreter {
    private int[] counts;

    @Override public synchronized List<KuudraEvent> interpret(KuudraEvent event, EventContext context) {
        if (forbidden(event)) { reset(); return List.of(); }
        for (int index = 0; index < requirements.size(); index++) {
            Requirement requirement = requirements.get(index);
            if (counts[index] < requirement.count() && EventSupport.matches(event, requirement.selector())) {
                counts[index]++; record(event);
                boolean complete = true;
                for (int i = 0; i < counts.length; i++) complete &= counts[i] == requirements.get(i).count();
                return complete ? complete("any-order-event") : List.of();
            }
        }
        return List.of();
    }
    @Override protected void resetProgress() { counts = requirements == null ? new int[0] : new int[requirements.size()]; }
}
