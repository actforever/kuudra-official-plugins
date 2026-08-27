package io.github.actforever.kuudra.conditionalboundary;

import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.List;

@io.github.actforever.kuudra.plugin.annotation.Egress("conditional-egress")
@ComponentDoc(purpose = "Exports an Event from the Session domain only when a resolved condition matches.",
        configuration = {
                @SpecProperty(path = "condition", type = Object.class, required = true,
                        description = "Actual value to evaluate; Kuudra placeholders may read Event, Session, Flow, or Global scope.",
                        examples = {"true", "\"completed\"", "3"}),
                @SpecProperty(path = "operator", type = String.class, defaultValue = "\"TRUTHY\"",
                        description = "Condition comparison operator.", examples = {"\"TRUTHY\"", "\"EQUALS\""},
                        allowedValues = {"TRUTHY", "FALSY", "EQUALS", "NOT_EQUALS", "GREATER_THAN",
                                "GREATER_THAN_OR_EQUALS", "LESS_THAN", "LESS_THAN_OR_EQUALS", "IN", "NOT_IN", "MATCHES_REGEX"}),
                @SpecProperty(path = "value", type = Object.class,
                        description = "Expected value used by binary operators.", examples = {"true", "5", "[\"ready\",\"done\"]"})
        })
public final class ConditionalEgress implements io.github.actforever.kuudra.api.component.Egress {
    @Override
    public List<KuudraEvent> export(KuudraEvent event, EventContext context) {
        return ConditionSupport.matches(context.configuration()) ? List.of(event) : List.of();
    }
}
