package io.github.actforever.kuudra.macro;

import io.github.actforever.kuudra.api.context.ContextValueReference;
import io.github.actforever.kuudra.api.event.EventDomain;
import io.github.actforever.kuudra.api.event.EventHandlerContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import java.math.BigDecimal;
import java.util.*;

public record MacroCondition(String referenceExpression, ContextValueReference reference, Operator operator, Object expected) {
    public enum Operator { EXISTS, NOT_EXISTS, TRUTHY, FALSY, EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUALS, LESS_THAN, LESS_THAN_OR_EQUALS, IN, NOT_IN, MATCHES_REGEX }
    public static MacroCondition ref(String path, Operator operator, Object expected) { return new MacroCondition(path, ContextValueReference.compile(path, EventDomain.SESSION), operator, expected); }
    public boolean matches(KuudraEvent event, EventHandlerContext context) {
        Optional<Object> actual = reference.find(event, context);
        return switch (operator) {
            case EXISTS -> actual.isPresent(); case NOT_EXISTS -> actual.isEmpty();
            case TRUTHY -> truthy(require(actual)); case FALSY -> !truthy(require(actual));
            case EQUALS -> equal(require(actual), expected); case NOT_EQUALS -> !equal(require(actual), expected);
            case GREATER_THAN -> compare(require(actual), expected) > 0; case GREATER_THAN_OR_EQUALS -> compare(require(actual), expected) >= 0;
            case LESS_THAN -> compare(require(actual), expected) < 0; case LESS_THAN_OR_EQUALS -> compare(require(actual), expected) <= 0;
            case IN -> expected instanceof Collection<?> values && values.stream().anyMatch(value -> equal(require(actual), value));
            case NOT_IN -> !(expected instanceof Collection<?> values) || values.stream().noneMatch(value -> equal(require(actual), value));
            case MATCHES_REGEX -> java.util.regex.Pattern.matches(String.valueOf(expected), String.valueOf(require(actual)));
        };
    }
    @Override public boolean equals(Object value) { return value instanceof MacroCondition other && Objects.equals(referenceExpression, other.referenceExpression) && operator == other.operator && Objects.equals(expected, other.expected); }
    @Override public int hashCode() { return Objects.hash(referenceExpression, operator, expected); }
    private static Object require(Optional<Object> value) { return value.orElseThrow(() -> new IllegalStateException("Macro condition reference is missing")); }
    private static boolean truthy(Object value) { if(value instanceof Boolean b)return b;if(value instanceof Number n)return decimal(n).compareTo(BigDecimal.ZERO)!=0;if(value instanceof CharSequence s)return !s.toString().isBlank()&&!"false".equalsIgnoreCase(s.toString());if(value instanceof Collection<?> c)return !c.isEmpty();if(value instanceof Map<?,?>m)return !m.isEmpty();return value!=null; }
    private static boolean equal(Object a,Object b){return a instanceof Number&&b instanceof Number?decimal(a).compareTo(decimal(b))==0:Objects.equals(a,b);}
    private static int compare(Object a,Object b){if(a instanceof Number&&b instanceof Number)return decimal(a).compareTo(decimal(b));if(a instanceof String x&&b instanceof String y)return x.compareTo(y);throw new IllegalArgumentException("Macro condition values are not comparable");}
    private static BigDecimal decimal(Object value){return new BigDecimal(value.toString());}
}
