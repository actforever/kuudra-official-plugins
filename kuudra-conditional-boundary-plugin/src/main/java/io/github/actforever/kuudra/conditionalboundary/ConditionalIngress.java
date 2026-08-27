package io.github.actforever.kuudra.conditionalboundary;

import io.github.actforever.kuudra.api.component.IngressDecision;
import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.api.session.SessionDependencyRequirement;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.List;
import java.util.Map;

@io.github.actforever.kuudra.plugin.annotation.Ingress("conditional-ingress")
@ComponentDoc(purpose = "Admits an Event only when a resolved condition matches and may bind the new Session to an active Session dependency graph.",
        configuration = {
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
                @SpecProperty(path = "dependencies", type = List.class,
                        description = "Active Session dependency requirements resolved atomically when this admission actually starts.",
                        examples = {"[{\"selector\":{\"flowId\":\"dev/window\",\"groupKey\":\"main\",\"matchPolicy\":\"UNIQUE\"},\"terminationPolicy\":\"CANCEL_DEPENDENT\"}]"}),
                @SpecProperty(path = "dependencies[]", type = Map.class,
                        description = "One dependency requirement containing selector and terminationPolicy."),
                @SpecProperty(path = "dependencies[].selector", type = Map.class,
                        description = "Selector using optional flowId, ingressComponentId, groupKey, and matchPolicy fields."),
                @SpecProperty(path = "dependencies[].terminationPolicy", type = String.class,
                        description = "Direction in which a terminal state requests cancellation.",
                        allowedValues = {"CANCEL_DEPENDENT", "CANCEL_REQUIRED", "CANCEL_BOTH"}),
                @SpecProperty(path = "policy", type = String.class, description = "Normal group scheduling policy, evaluated before dependencies.",
                        allowedValues = {"PARALLEL", "SERIAL", "IGNORE", "CANCEL_AND_REPLACE_PENDING", "CANCEL_AND_KEEP_PENDING", "TOGGLE"}),
                @SpecProperty(path = "groupScope", type = String.class, description = "FLOW_BINDING or cross-Flow INGRESS group scope.",
                        allowedValues = {"FLOW_BINDING", "INGRESS"}),
                @SpecProperty(path = "maxParallelSessions", type = Integer.class, description = "Bound for PARALLEL sessions in one group."),
                @SpecProperty(path = "queueCapacity", type = Integer.class, description = "Bound for queued admissions in one group.")
        })
public final class ConditionalIngress implements io.github.actforever.kuudra.api.component.Ingress {
    @Override
    public IngressDecision admit(KuudraEvent event, EventContext context) {
        if (!ConditionSupport.matches(context.configuration())) return IngressDecision.reject("condition-not-matched");
        String groupKey = context.configuration("groupKey", String.class, event.type());
        Map<String, Object> initial = initialContext(context.configuration().get("initialSessionContext"));
        List<SessionDependencyRequirement> dependencies = DependencySupport.dependencies(context.configuration());
        return dependencies.isEmpty()
                ? new IngressDecision.Accepted(groupKey, event, initial)
                : IngressDecision.accept(groupKey, event, initial, dependencies);
    }

    private static Map<String, Object> initialContext(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("initialSessionContext must be an object");
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Map.copyOf(result);
    }
}
