package io.github.actforever.kuudra.conditionalboundary;

import io.github.actforever.kuudra.api.component.IngressDecision;
import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.annotation.ResourceDoc;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.Map;

@io.github.actforever.kuudra.plugin.annotation.Ingress("conditional-ingress")
@ResourceDoc(purpose = "Admits an Event only when a resolved condition matches, then assigns its Session group and labels.",
        arguments = {
                @SpecProperty(path = "condition", type = Object.class, required = true,
                        description = "Actual value to evaluate; Kuudra placeholders may read Event, Flow, or Global scope.",
                        examples = {"true", "\"enabled\"", "3"}),
                @SpecProperty(path = "operator", type = String.class, defaultValue = "\"TRUTHY\"",
                        description = "Condition comparison operator.", examples = {"\"TRUTHY\"", "\"EQUALS\""},
                        allowedValues = {"TRUTHY", "FALSY", "EQUALS", "NOT_EQUALS", "GREATER_THAN",
                                "GREATER_THAN_OR_EQUALS", "LESS_THAN", "LESS_THAN_OR_EQUALS", "IN", "NOT_IN", "MATCHES_REGEX"}),
                @SpecProperty(path = "value", type = Object.class,
                        description = "Expected value used by binary operators.", examples = {"true", "5", "[\"dev\",\"prod\"]"}),
                @SpecProperty(path = "groupKey", type = String.class,
                        description = "Session scheduling group; defaults to the Event type.", examples = {"\"keyboard\""}),
                @SpecProperty(path = "initialSessionContext", type = Map.class,
                        description = "Initial values copied into the admitted Session context.", examples = {"{\"mode\":\"macro\"}"}),
                @SpecProperty(path = "sessionLabels", type = Map.class,
                        description = "String labels assigned to the admitted Session and used by SessionCoordinationPolicy selectors.",
                        examples = {"{\"role\":\"job\"}", "{\"role\":\"window\",\"device\":\"keyboard-1\"}"})
        })
public final class ConditionalIngress implements io.github.actforever.kuudra.api.component.Ingress,
        io.github.actforever.kuudra.plugin.ResourceLifecycle {
    @Override
    public IngressDecision admit(KuudraEvent event, EventContext context) {
        if (!ConditionSupport.matches(context.configuration())) return IngressDecision.reject("condition-not-matched");
        String groupKey = context.configuration("groupKey", String.class, event.type());
        Map<String, Object> initial = initialContext(context.configuration().get("initialSessionContext"));
        return IngressDecision.accept(groupKey, event, initial, labels(context.configuration().get("sessionLabels")));
    }

    private static Map<String, Object> initialContext(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("initialSessionContext must be an object");
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Map.copyOf(result);
    }

    private static Map<String, String> labels(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("sessionLabels must be an object");
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), String.valueOf(item)));
        return Map.copyOf(result);
    }
}
