package io.github.actforever.kuudra.defaultplugin;

import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.context.ExecutionDecision;
import io.github.actforever.kuudra.api.event.EventData;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OfficialEventComponentsTest {
    @Test void mapperRetypesAndProjectsData() {
        KuudraEvent input = KuudraEvent.of("source", EventData.of("source", Map.of("message", "hello")));
        EventContext context = context(Map.of("outputType", "normalized", "preserveData", true,
                "data", Map.of("mapped", Map.of("message", "hello"))));
        KuudraEvent output = new EventMapper().adapt(input, context).get(0);
        assertEquals("normalized", output.type());
        assertEquals("hello", output.data().require("source", "message"));
        assertEquals("hello", output.data().require("mapped", "message"));
    }

    @Test void filterSupportsNestedValuesAndRuleAggregation() {
        KuudraEvent input = KuudraEvent.of("normalized", EventData.of("mapped", Map.of("payload", Map.of("count", 3))));
        EventFilter filter = new EventFilter();
        assertEquals(List.of(input), filter.adapt(input, context(Map.of("rules", List.of(
                Map.of("path", "type", "operator", "EQUALS", "value", "normalized"),
                Map.of("path", "mapped.payload.count", "operator", "GREATER_THAN", "value", 2))))));
        assertTrue(filter.adapt(input, context(Map.of("rules", List.of(Map.of("path", "type", "value", "other"))))).isEmpty());
    }

    @Test void sequentialInterpreterResetsOnWrongSequenceMember() {
        SequentialEventInterpreter interpreter = sequential();
        interpreter.start().toCompletableFuture().join();
        assertTrue(interpreter.interpret(event("a"), context(Map.of())).isEmpty());
        assertTrue(interpreter.interpret(event("a"), context(Map.of())).isEmpty());
        assertTrue(interpreter.interpret(event("b"), context(Map.of())).isEmpty());
        assertTrue(interpreter.interpret(event("a"), context(Map.of())).isEmpty());
        List<KuudraEvent> output = interpreter.interpret(event("b"), context(Map.of()));
        assertEquals("sequence.complete", output.get(0).type());
        assertEquals(2, output.get(0).data().require("kuudra-official", "matchCount"));
        interpreter.stop().toCompletableFuture().join();
    }

    @Test void anyOrderInterpreterCompletesRegardlessOfOrder() {
        AnyOrderEventInterpreter interpreter = new AnyOrderEventInterpreter();
        configure(interpreter, List.of(requirement("a", 1), requirement("b", 1)), "chord.complete");
        interpreter.start().toCompletableFuture().join();
        assertTrue(interpreter.interpret(event("b"), context(Map.of())).isEmpty());
        assertEquals("chord.complete", interpreter.interpret(event("a"), context(Map.of())).get(0).type());
        interpreter.stop().toCompletableFuture().join();
    }

    private SequentialEventInterpreter sequential() {
        SequentialEventInterpreter result = new SequentialEventInterpreter();
        configure(result, List.of(requirement("a", 1), requirement("b", 1)), "sequence.complete");
        return result;
    }
    private void configure(AbstractWindowInterpreter target, List<AbstractWindowInterpreter.Requirement> requirements, String outputType) {
        target.requirements = requirements; target.outputType = outputType; target.timeoutMs = 1000;
        target.includeMatchedEvents = true; target.forbidden = List.of(); target.resetProgress();
    }
    private AbstractWindowInterpreter.Requirement requirement(String type, int count) {
        return new AbstractWindowInterpreter.Requirement(Map.of("type", type), count);
    }
    private KuudraEvent event(String type) { return KuudraEvent.of(type, EventData.empty()); }
    private EventContext context(Map<String, Object> options) {
        return new EventContext("test", null, Map.of(), null, () -> ExecutionDecision.CONTINUE, Map.of(), options);
    }
}
