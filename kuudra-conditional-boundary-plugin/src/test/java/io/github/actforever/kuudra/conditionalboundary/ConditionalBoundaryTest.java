package io.github.actforever.kuudra.conditionalboundary;

import io.github.actforever.kuudra.api.component.IngressDecision;
import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.context.ExecutionDecision;
import io.github.actforever.kuudra.api.event.EventData;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.api.session.SessionMatchPolicy;
import io.github.actforever.kuudra.api.session.SessionTerminationPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionalBoundaryTest {
    private final KuudraEvent event = KuudraEvent.of("hello", EventData.empty());

    @Test
    void ingressRejectsFalseConditionAndAdmitsNativeNumericComparison() {
        ConditionalIngress ingress = new ConditionalIngress();
        assertInstanceOf(IngressDecision.Rejected.class,
                ingress.admit(event, context(Map.of("condition", false))));
        IngressDecision.Accepted accepted = assertInstanceOf(IngressDecision.Accepted.class,
                ingress.admit(event, context(Map.of("condition", 3, "operator", "GREATER_THAN", "value", 2,
                        "groupKey", "jobs", "initialSessionContext", Map.of("mode", "macro")))));
        assertEquals("jobs", accepted.groupKey());
        assertEquals("macro", accepted.initialSessionContext().get("mode"));
    }

    @Test
    void ingressReturnsTypedDependencyRequirements() {
        Map<String, Object> dependency = Map.of(
                "selector", Map.of("flowId", "dev/window", "ingressComponentId", "ingress/dev/window",
                        "groupKey", "main", "matchPolicy", "LATEST"),
                "terminationPolicy", "CANCEL_BOTH");
        IngressDecision.ConstrainedAccepted accepted = assertInstanceOf(IngressDecision.ConstrainedAccepted.class,
                new ConditionalIngress().admit(event, context(Map.of("condition", true, "dependencies", List.of(dependency)))));
        assertEquals(SessionMatchPolicy.LATEST, accepted.dependencies().get(0).selector().matchPolicy());
        assertEquals(SessionTerminationPolicy.CANCEL_BOTH, accepted.dependencies().get(0).terminationPolicy());
    }

    @Test
    void egressExportsOnlyMatchingEvents() {
        ConditionalEgress egress = new ConditionalEgress();
        assertEquals(List.of(event), egress.export(event,
                context(Map.of("condition", "ready", "operator", "IN", "value", List.of("ready", "done")))));
        assertTrue(egress.export(event, context(Map.of("condition", "waiting", "operator", "EQUALS", "value", "ready"))).isEmpty());
    }

    @Test
    void malformedDependenciesFailFast() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ConditionalIngress().admit(event, context(Map.of("condition", true, "dependencies", "wrong"))));
        assertTrue(error.getMessage().contains("dependencies"));
    }

    private EventContext context(Map<String, Object> options) {
        return new EventContext("test", null, Map.of(), null, () -> ExecutionDecision.CONTINUE, Map.of(), options);
    }
}
