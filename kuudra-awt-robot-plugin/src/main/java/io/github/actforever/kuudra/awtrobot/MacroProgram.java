package io.github.actforever.kuudra.awtrobot;

import io.github.actforever.kuudra.api.KuudraException;
import io.github.actforever.kuudra.api.action.ActionContext;
import io.github.actforever.kuudra.api.context.*;
import io.github.actforever.kuudra.api.event.*;
import io.github.actforever.kuudra.interaction.*;

import java.util.*;

final class MacroProgram {
    private static final int MAX_DEPTH = 32;
    private final List<Step> steps;
    private final long maxTotalSteps;
    private final long markerLifetimeMillis;

    private MacroProgram(List<Step> steps, long maxTotalSteps, long markerLifetimeMillis) {
        this.steps = steps; this.maxTotalSteps = maxTotalSteps; this.markerLifetimeMillis = markerLifetimeMillis;
    }

    static MacroProgram parse(Map<String, Object> configuration) {
        long maximum = number(configuration.getOrDefault("maxTotalSteps", 10_000), "maxTotalSteps");
        long marker = number(configuration.getOrDefault("syntheticMarkerLifetimeMillis", 500), "syntheticMarkerLifetimeMillis");
        if (maximum < 1 || maximum > 1_000_000) throw invalid("maxTotalSteps must be between 1 and 1000000");
        if (marker < 1 || marker > 60_000) throw invalid("syntheticMarkerLifetimeMillis must be between 1 and 60000");
        return new MacroProgram(parseSteps(list(configuration.get("steps"), "steps"), 0), maximum, marker);
    }

    void execute(KuudraEvent event, ActionContext context, RobotDriver driver) {
        Frame frame = new Frame(event, context, new InputState(driver, markerLifetimeMillis), maxTotalSteps);
        try {
            Signal signal = run(steps, frame);
            if (signal == Signal.BREAK) throw invalid("break may only be used inside a loop");
        }
        catch (CancelSignal ignored) { }
        finally { frame.inputs.releaseAll(); }
    }

    private static Signal run(List<Step> steps, Frame frame) {
        for (Step step : steps) {
            frame.checkpoint(); frame.consumeStep();
            Signal signal = step.run(frame);
            if (signal != Signal.CONTINUE) return signal;
            frame.checkpoint();
        }
        return Signal.CONTINUE;
    }

    private static List<Step> parseSteps(List<?> values, int depth) {
        if (depth > MAX_DEPTH) throw invalid("Macro nesting exceeds " + MAX_DEPTH);
        List<Step> steps = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> item = map(value, "step");
            String action = text(item.get("action"), "step.action");
            steps.add(switch (action) {
                case "keyPress" -> frame -> { frame.inputs.keyPress(decode(item.get("key"), KeySpec.class, "key")); return Signal.CONTINUE; };
                case "keyRelease" -> frame -> { frame.inputs.keyRelease(decode(item.get("key"), KeySpec.class, "key")); return Signal.CONTINUE; };
                case "keyTap" -> tapKey(item);
                case "type" -> type(item);
                case "mousePress" -> frame -> { frame.inputs.mousePress(decode(item.get("button"), MouseButtonSpec.class, "button")); return Signal.CONTINUE; };
                case "mouseRelease" -> frame -> { frame.inputs.mouseRelease(decode(item.get("button"), MouseButtonSpec.class, "button")); return Signal.CONTINUE; };
                case "mouseClick" -> clickMouse(item);
                case "mouseMove" -> frame -> { frame.inputs.mouseMove(decode(item.get("position"), ScreenPosition.class, "position")); return Signal.CONTINUE; };
                case "mouseWheel" -> frame -> { frame.inputs.mouseWheel(decode(item.get("wheel"), MouseWheelSpec.class, "wheel")); return Signal.CONTINUE; };
                case "sleep" -> frame -> { frame.sleep(number(item.get("durationMillis"), "durationMillis")); return Signal.CONTINUE; };
                case "if" -> conditional(item, depth);
                case "loop" -> loop(item, depth);
                case "break" -> control(item, Signal.BREAK);
                case "return" -> control(item, Signal.RETURN);
                case "cancelSession" -> cancel(item);
                case "emit" -> emit(item);
                default -> throw invalid("Unsupported macro action: " + action);
            });
        }
        return List.copyOf(steps);
    }

    private static Step tapKey(Map<String, Object> item) {
        return frame -> {
            KeySpec key = decode(item.get("key"), KeySpec.class, "key");
            frame.inputs.keyPress(key);
            try { frame.sleep(number(item.getOrDefault("holdMillis", 50), "holdMillis")); }
            finally { frame.inputs.keyRelease(key); }
            return Signal.CONTINUE;
        };
    }

    private static Step type(Map<String, Object> item) {
        List<KeySpec> keys = list(item.get("keys"), "keys").stream().map(value -> decode(value, KeySpec.class, "keys[]")).toList();
        List<ModifierKey> modifiers = item.containsKey("modifiers") ? list(item.get("modifiers"), "modifiers").stream()
                .map(value -> decode(value, ModifierKey.class, "modifiers[]")).toList() : List.of();
        long hold = number(item.getOrDefault("holdMillis", 25), "holdMillis");
        long interval = number(item.getOrDefault("intervalMillis", 0), "intervalMillis");
        return frame -> {
            List<KeySpec> pressedModifiers = modifiers.stream().map(KeyMapper::modifier).toList();
            pressedModifiers.forEach(frame.inputs::keyPress);
            try {
                for (KeySpec key : keys) {
                    frame.checkpoint(); frame.inputs.keyPress(key);
                    try { frame.sleep(hold); } finally { frame.inputs.keyRelease(key); }
                    if (interval > 0) frame.sleep(interval);
                }
            } finally { for (int index = pressedModifiers.size() - 1; index >= 0; index--) frame.inputs.keyRelease(pressedModifiers.get(index)); }
            return Signal.CONTINUE;
        };
    }

    private static Step clickMouse(Map<String, Object> item) {
        return frame -> {
            MouseButtonSpec button = decode(item.get("button"), MouseButtonSpec.class, "button");
            frame.inputs.mousePress(button);
            try { frame.sleep(number(item.getOrDefault("holdMillis", 50), "holdMillis")); }
            finally { frame.inputs.mouseRelease(button); }
            return Signal.CONTINUE;
        };
    }

    private static Step conditional(Map<String, Object> item, int depth) {
        Condition condition = Condition.parse(map(item.get("condition"), "condition"));
        List<Step> thenSteps = parseSteps(list(item.get("then"), "then"), depth + 1);
        List<Step> elseSteps = item.containsKey("else") ? parseSteps(list(item.get("else"), "else"), depth + 1) : List.of();
        return frame -> run(condition.matches(frame) ? thenSteps : elseSteps, frame);
    }

    private static Step loop(Map<String, Object> item, int depth) {
        Long count = item.containsKey("count") ? number(item.get("count"), "count") : null;
        Long maximum = item.containsKey("maxIterations") ? number(item.get("maxIterations"), "maxIterations") : count;
        Condition condition = item.containsKey("while") ? Condition.parse(map(item.get("while"), "while")) : null;
        if (maximum == null || maximum < 0) throw invalid("loop requires count or a non-negative maxIterations");
        if (count != null && count < 0) throw invalid("loop count must be non-negative");
        List<Step> body = parseSteps(list(item.get("steps"), "steps"), depth + 1);
        return frame -> {
            long iterations = count == null ? maximum : Math.min(count, maximum);
            for (long index = 0; index < iterations; index++) {
                frame.checkpoint();
                if (condition != null && !condition.matches(frame)) break;
                Signal signal = run(body, frame);
                if (signal == Signal.BREAK) break;
                if (signal != Signal.CONTINUE) return signal;
            }
            return Signal.CONTINUE;
        };
    }

    private static Step control(Map<String, Object> item, Signal signal) {
        Condition condition = item.containsKey("when") ? Condition.parse(map(item.get("when"), "when")) : null;
        return frame -> condition == null || condition.matches(frame) ? signal : Signal.CONTINUE;
    }

    private static Step cancel(Map<String, Object> item) {
        Condition condition = item.containsKey("when") ? Condition.parse(map(item.get("when"), "when")) : null;
        String reason = String.valueOf(item.getOrDefault("reason", "macro-requested"));
        return frame -> {
            if (condition == null || condition.matches(frame)) {
                frame.context.sessionControl().requestCancellation(reason);
                return Signal.CANCEL;
            }
            return Signal.CONTINUE;
        };
    }

    private static Step emit(Map<String, Object> item) {
        String type = text(item.get("eventType"), "eventType");
        boolean copy = Boolean.TRUE.equals(decode(item.getOrDefault("copyInputData", false), Boolean.class, "copyInputData"));
        Map<String, Object> configured = item.containsKey("data") ? map(item.get("data"), "data") : Map.of();
        return frame -> {
            EventData data = copy ? frame.event.data() : EventData.empty();
            for (Map.Entry<String, Object> namespace : configured.entrySet()) {
                Map<String, Object> values = map(namespace.getValue(), "data." + namespace.getKey());
                for (Map.Entry<String, Object> value : values.entrySet()) data = data.with(namespace.getKey(), value.getKey(), value.getValue());
            }
            frame.context.emit(KuudraEvent.of(type, data));
            return Signal.CONTINUE;
        };
    }

    private interface Step { Signal run(Frame frame); }
    private enum Signal { CONTINUE, BREAK, RETURN, CANCEL }

    private static final class Frame {
        final KuudraEvent event; final ActionContext context; final InputState inputs;
        long remainingSteps;
        Frame(KuudraEvent event, ActionContext context, InputState inputs, long remainingSteps) {
            this.event = event; this.context = context; this.inputs = inputs; this.remainingSteps = remainingSteps;
        }
        void consumeStep() { if (--remainingSteps < 0) throw invalid("Macro exceeded maxTotalSteps"); }
        void checkpoint() {
            ExecutionDecision decision = context.executionControl().poll();
            if (decision == ExecutionDecision.CANCEL) throw new CancelSignal();
            if (decision != ExecutionDecision.PAUSE) return;
            inputs.suspend();
            decision = context.executionControl().checkpoint().toCompletableFuture().join();
            if (decision == ExecutionDecision.CANCEL) throw new CancelSignal();
            inputs.resume();
        }
        void sleep(long millis) {
            if (millis < 0) throw invalid("duration must be non-negative");
            long remaining = millis;
            while (remaining > 0) {
                checkpoint(); long chunk = Math.min(remaining, 25);
                long before = System.nanoTime();
                try { Thread.sleep(chunk); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new CancelSignal(); }
                long elapsed = Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before));
                remaining = Math.max(0, remaining - elapsed);
            }
        }
    }

    private record Condition(ContextValueReference reference, String operator, Object expected) {
        static Condition parse(Map<String, Object> value) {
            String ref = text(value.get("ref"), "condition.ref");
            String operator = String.valueOf(value.getOrDefault("operator", "TRUTHY")).toUpperCase(Locale.ROOT);
            return new Condition(ContextValueReference.compile(ref, EventDomain.SESSION), operator, value.get("value"));
        }
        boolean matches(Frame frame) {
            Optional<Object> actual = reference.find(frame.event, frame.context);
            return switch (operator) {
                case "EXISTS" -> actual.isPresent(); case "NOT_EXISTS" -> actual.isEmpty();
                case "TRUTHY" -> truthy(require(actual)); case "FALSY" -> !truthy(require(actual));
                case "EQUALS" -> equal(require(actual), expected); case "NOT_EQUALS" -> !equal(require(actual), expected);
                case "GREATER_THAN" -> compare(require(actual), expected) > 0;
                case "GREATER_THAN_OR_EQUALS" -> compare(require(actual), expected) >= 0;
                case "LESS_THAN" -> compare(require(actual), expected) < 0;
                case "LESS_THAN_OR_EQUALS" -> compare(require(actual), expected) <= 0;
                case "IN" -> expected instanceof Collection<?> values && values.stream().anyMatch(value -> equal(require(actual), value));
                case "NOT_IN" -> !(expected instanceof Collection<?> values) || values.stream().noneMatch(value -> equal(require(actual), value));
                case "MATCHES_REGEX" -> java.util.regex.Pattern.matches(String.valueOf(expected), String.valueOf(require(actual)));
                default -> throw invalid("Unsupported condition operator: " + operator);
            };
        }
        private static Object require(Optional<Object> value) { return value.orElseThrow(() -> invalid("Condition reference is missing")); }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return decimal(number).compareTo(java.math.BigDecimal.ZERO) != 0;
        if (value instanceof CharSequence text) return !text.toString().isBlank() && !"false".equalsIgnoreCase(text.toString());
        if (value instanceof Collection<?> values) return !values.isEmpty();
        if (value instanceof Map<?, ?> values) return !values.isEmpty();
        return value != null;
    }
    private static boolean equal(Object left, Object right) {
        return left instanceof Number && right instanceof Number ? decimal(left).compareTo(decimal(right)) == 0 : Objects.equals(left, right);
    }
    private static int compare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) return decimal(left).compareTo(decimal(right));
        if (left instanceof String a && right instanceof String b) return a.compareTo(b);
        throw invalid("Condition values are not comparable");
    }
    private static java.math.BigDecimal decimal(Object value) { return new java.math.BigDecimal(value.toString()); }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value, String path) {
        if (!(value instanceof Map<?, ?> input)) throw invalid(path + " must be an object");
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        input.forEach((key, item) -> { if (!(key instanceof String text)) throw invalid(path + " keys must be strings"); output.put(text, item); });
        return Map.copyOf(output);
    }
    private static List<?> list(Object value, String path) { if (!(value instanceof List<?> list)) throw invalid(path + " must be an array"); return list; }
    private static String text(Object value, String path) { if (!(value instanceof String text) || text.isBlank()) throw invalid(path + " must be text"); return text; }
    private static long number(Object value, String path) { if (!(value instanceof Number number)) throw invalid(path + " must be a number"); return number.longValue(); }
    private static <T> T decode(Object value, Class<T> type, String path) {
        if (value == null) throw invalid(path + " is required");
        try { return ContextCodecs.defaultCodec().decode(value, type); }
        catch (RuntimeException error) { throw new KuudraException("Invalid macro value at " + path + ": expected " + type.getSimpleName(), error); }
    }
    private static KuudraException invalid(String message) { return new KuudraException(message); }
    private static final class CancelSignal extends RuntimeException { }
}
