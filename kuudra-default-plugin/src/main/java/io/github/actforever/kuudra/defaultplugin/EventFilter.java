package io.github.actforever.kuudra.defaultplugin;

import io.github.actforever.kuudra.api.context.EventContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@io.github.actforever.kuudra.plugin.annotation.EventAdapter("event-filter")
@ComponentDoc(purpose="Passes only Events matching declarative rules.", configuration={
        @SpecProperty(path="mode", type=String.class, description="Rule aggregation mode.", defaultValue="ALL", allowedValues={"ALL","ANY"}, examples={"\"ALL\""}),
        @SpecProperty(path="negate", type=Boolean.class, description="Negates the aggregated result.", defaultValue="false", examples={"false"}),
        @SpecProperty(path="rules[]", type=Map.class, required=true, description="Rules with path, operator and optional value.", examples={"{\"path\":\"type\",\"operator\":\"EQUALS\",\"value\":\"demo.normalized\"}"})
})
public final class EventFilter implements io.github.actforever.kuudra.api.component.EventAdapter {
    @Override public List<KuudraEvent> adapt(KuudraEvent event, EventContext context) {
        List<?> rules = context.configuration("rules", List.class);
        if (rules.isEmpty()) throw new IllegalArgumentException("rules must not be empty");
        boolean any = "ANY".equalsIgnoreCase(context.configuration("mode", String.class, "ALL"));
        boolean matched = any ? rules.stream().anyMatch(rule -> match(event, rule)) : rules.stream().allMatch(rule -> match(event, rule));
        if (context.configuration("negate", Boolean.class, false)) matched = !matched;
        return matched ? List.of(event) : List.of();
    }

    private boolean match(KuudraEvent event, Object value) {
        if (!(value instanceof Map<?, ?> rule)) throw new IllegalArgumentException("Each filter rule must be an object");
        String path = required(rule, "path");
        Object configuredOperator = rule.get("operator");
        String operator = String.valueOf(configuredOperator == null ? "EQUALS" : configuredOperator).toUpperCase();
        Object actual = EventSupport.value(event, path), expected = rule.get("value");
        return switch (operator) {
            case "EXISTS" -> actual != null;
            case "NOT_EXISTS" -> actual == null;
            case "EQUALS" -> EventSupport.equal(actual, expected);
            case "NOT_EQUALS" -> !EventSupport.equal(actual, expected);
            case "IN", "NOT_IN" -> operator.equals("IN") == (expected instanceof Collection<?> c && c.stream().anyMatch(item -> EventSupport.equal(actual, item)));
            case "GREATER_THAN" -> EventSupport.compare(actual, expected) > 0;
            case "GREATER_OR_EQUAL" -> EventSupport.compare(actual, expected) >= 0;
            case "LESS_THAN" -> EventSupport.compare(actual, expected) < 0;
            case "LESS_OR_EQUAL" -> EventSupport.compare(actual, expected) <= 0;
            case "CONTAINS" -> actual instanceof String text && text.contains(String.valueOf(expected));
            case "STARTS_WITH" -> actual instanceof String text && text.startsWith(String.valueOf(expected));
            case "ENDS_WITH" -> actual instanceof String text && text.endsWith(String.valueOf(expected));
            case "MATCHES_REGEX" -> actual instanceof String text && Pattern.matches(String.valueOf(expected), text);
            default -> throw new IllegalArgumentException("Unsupported filter operator: " + operator);
        };
    }

    private String required(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Filter rule requires " + key);
        return text;
    }
}
