package io.github.actforever.kuudra.conditionalboundary;

import io.github.actforever.kuudra.api.component.IngressDecision;
import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.context.ExecutionDecision;
import io.github.actforever.kuudra.api.event.EventData;
import io.github.actforever.kuudra.api.event.KuudraEvent;
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
    void ingressReturnsSessionLabelsForPolicySelection() {
        IngressDecision.Accepted accepted = assertInstanceOf(IngressDecision.Accepted.class,
                new ConditionalIngress().admit(event, context(Map.of("condition", true,
                        "sessionLabels", Map.of("role", "job", "device", "keyboard-1")))));
        assertEquals(Map.of("role", "job", "device", "keyboard-1"), accepted.sessionLabels());
    }

    @Test
    void egressExportsOnlyMatchingEvents() {
        ConditionalEgress egress = new ConditionalEgress();
        assertEquals(List.of(event), egress.export(event,
                context(Map.of("condition", "ready", "operator", "IN", "value", List.of("ready", "done")))));
        assertTrue(egress.export(event, context(Map.of("condition", "waiting", "operator", "EQUALS", "value", "ready"))).isEmpty());
    }

    @Test
    void malformedSessionLabelsFailFast() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ConditionalIngress().admit(event, context(Map.of("condition", true, "sessionLabels", "wrong"))));
        assertTrue(error.getMessage().contains("sessionLabels"));
    }

    private EventContext context(Map<String, Object> options) {
        return new EventContext("test", null, Map.of(), null, () -> ExecutionDecision.CONTINUE, Map.of(), options);
    }
}
