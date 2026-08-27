package io.github.actforever.kuudra.defaultplugin;

import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.List;

@io.github.actforever.kuudra.plugin.annotation.EventInterpreter("sequential-event")
@ComponentDoc(purpose="Recognizes an ordered Event sequence inside a bounded time window.", configuration={
        @SpecProperty(path="outputType", type=String.class, required=true, description="Type emitted after a complete match.", examples={"\"gesture.sequence\""}),
        @SpecProperty(path="timeoutMs", type=Long.class, description="Window timeout in milliseconds.", defaultValue="3000", examples={"2000"}),
        @SpecProperty(path="requirements[]", type=java.util.Map.class, required=true, description="Ordered selector/count requirements.", examples={"[{selector: {type: key.down}, count: 2}]"}),
        @SpecProperty(path="forbidden[]", type=java.util.Map.class, description="Selectors that reset progress.", examples={"[{type: key.escape}]"}),
        @SpecProperty(path="includeMatchedEvents", type=Boolean.class, defaultValue="true", description="Includes immutable matched Event snapshots.", examples={"true"})
})
public final class SequentialEventInterpreter extends AbstractWindowInterpreter {
    private int requirementIndex;
    private int currentCount;

    @Override public synchronized List<KuudraEvent> interpret(KuudraEvent event, EventContext context) {
        if (forbidden(event)) { reset(); return List.of(); }
        Requirement expected = requirements.get(requirementIndex);
        if (EventSupport.matches(event, expected.selector())) {
            record(event);
            if (++currentCount == expected.count()) { currentCount = 0; requirementIndex++; }
            if (requirementIndex == requirements.size()) return complete("sequential-event");
            return List.of();
        }
        boolean belongsToSequence = requirements.stream().anyMatch(requirement -> EventSupport.matches(event, requirement.selector()));
        if (belongsToSequence) reset();
        return List.of();
    }
    @Override protected void resetProgress() { requirementIndex = 0; currentCount = 0; }
}
