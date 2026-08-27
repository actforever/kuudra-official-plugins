package io.github.actforever.kuudra.defaultplugin;

import io.github.actforever.kuudra.api.event.KuudraEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class EventSupport {
    private EventSupport() {}

    static Object value(KuudraEvent event, String path) {
        if ("type".equals(path)) return event.type();
        String[] parts = path.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Event path must be 'type' or '<namespace>.<field>': " + path);
        Object current = event.data().namespace(parts[0]).get(parts[1]);
        for (int i = 2; i < parts.length && current != null; i++) {
            if (current instanceof Map<?, ?> map) current = map.get(parts[i]);
            else if (current instanceof List<?> list && parts[i].matches("\\d+")) current = list.get(Integer.parseInt(parts[i]));
            else return null;
        }
        return current;
    }

    static boolean equal(Object actual, Object expected) {
        if (actual instanceof Number && expected instanceof Number) {
            return decimal(actual).compareTo(decimal(expected)) == 0;
        }
        return Objects.equals(actual, expected);
    }

    static int compare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) return decimal(left).compareTo(decimal(right));
        if (left instanceof String a && right instanceof String b) return a.compareTo(b);
        throw new IllegalArgumentException("Values are not comparable: " + left + ", " + right);
    }

    static boolean matches(KuudraEvent event, Map<String, Object> selector) {
        if (selector == null || selector.isEmpty()) throw new IllegalArgumentException("selector must not be empty");
        return selector.entrySet().stream().allMatch(entry -> equal(value(event, entry.getKey()), entry.getValue()));
    }

    private static BigDecimal decimal(Object value) { return new BigDecimal(value.toString()); }
}
