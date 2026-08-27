package io.github.actforever.kuudra.conditionalboundary;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared evaluation for conditional RAW/SESSION boundaries. Placeholder lookup is compiled by Runtime. */
final class ConditionSupport {
    private ConditionSupport() { }

    static boolean matches(Map<String, Object> configuration) {
        Object actual = configuration.get("condition");
        String operator = String.valueOf(configuration.getOrDefault("operator", "TRUTHY")).toUpperCase(java.util.Locale.ROOT);
        Object expected = configuration.get("value");
        return switch (operator) {
            case "TRUTHY" -> truthy(actual);
            case "FALSY" -> !truthy(actual);
            case "EQUALS" -> equal(actual, expected);
            case "NOT_EQUALS" -> !equal(actual, expected);
            case "GREATER_THAN" -> compare(actual, expected) > 0;
            case "GREATER_THAN_OR_EQUALS" -> compare(actual, expected) >= 0;
            case "LESS_THAN" -> compare(actual, expected) < 0;
            case "LESS_THAN_OR_EQUALS" -> compare(actual, expected) <= 0;
            case "IN" -> expected instanceof Collection<?> values && values.stream().anyMatch(value -> equal(actual, value));
            case "NOT_IN" -> !(expected instanceof Collection<?> values) || values.stream().noneMatch(value -> equal(actual, value));
            case "MATCHES_REGEX" -> actual != null && expected != null && Pattern.matches(expected.toString(), actual.toString());
            default -> throw new IllegalArgumentException("Unsupported condition operator: " + operator);
        };
    }

    private static boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return decimal(number).compareTo(BigDecimal.ZERO) != 0;
        if (value instanceof CharSequence text) return !text.toString().isBlank() && !"false".equalsIgnoreCase(text.toString());
        if (value instanceof Collection<?> collection) return !collection.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        return true;
    }

    private static boolean equal(Object actual, Object expected) {
        if (actual instanceof Number && expected instanceof Number) return decimal(actual).compareTo(decimal(expected)) == 0;
        return Objects.equals(actual, expected);
    }

    private static int compare(Object actual, Object expected) {
        if (actual instanceof Number && expected instanceof Number) return decimal(actual).compareTo(decimal(expected));
        if (actual instanceof String left && expected instanceof String right) return left.compareTo(right);
        throw new IllegalArgumentException("Condition values are not comparable: " + actual + ", " + expected);
    }

    private static BigDecimal decimal(Object value) { return new BigDecimal(value.toString()); }
}
