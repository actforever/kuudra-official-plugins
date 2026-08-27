package io.github.actforever.kuudra.conditionalboundary;

import io.github.actforever.kuudra.api.session.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DependencySupport {
    private DependencySupport() { }

    static List<SessionDependencyRequirement> dependencies(Map<String, Object> configuration) {
        Object configured = configuration.get("dependencies");
        if (configured == null) return List.of();
        if (!(configured instanceof List<?> entries)) throw new IllegalArgumentException("dependencies must be a list");
        List<SessionDependencyRequirement> result = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            if (!(entries.get(index) instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException("dependencies[" + index + "] must be an object");
            }
            Object selectorValue = entry.get("selector");
            if (!(selectorValue instanceof Map<?, ?> selector)) {
                throw new IllegalArgumentException("dependencies[" + index + "].selector must be an object");
            }
            result.add(new SessionDependencyRequirement(new SessionSelector(
                    text(selector.get("flowId")), text(selector.get("ingressComponentId")), text(selector.get("groupKey")),
                    enumValue(SessionMatchPolicy.class, selector.get("matchPolicy"), SessionMatchPolicy.UNIQUE)),
                    enumValue(SessionTerminationPolicy.class, entry.get("terminationPolicy"),
                            SessionTerminationPolicy.CANCEL_DEPENDENT)));
        }
        return List.copyOf(result);
    }

    private static String text(Object value) { return value == null ? null : value.toString(); }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value, E fallback) {
        return value == null ? fallback : Enum.valueOf(type, value.toString().replace('-', '_').toUpperCase(java.util.Locale.ROOT));
    }
}
