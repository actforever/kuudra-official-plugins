package io.github.actforever.kuudra.awtrobot;

import io.github.actforever.kuudra.api.context.*;
import io.github.actforever.kuudra.api.event.*;
import io.github.actforever.kuudra.api.session.CurrentSessionControl;
import io.github.actforever.kuudra.interaction.*;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.*;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class MacroProgramTest {
    @Test
    void executesTypedDeviceStepsAndEmitsConfiguredEvent() {
        RecordingDriver driver = new RecordingDriver();
        List<KuudraEvent> emitted = new ArrayList<>();
        KeySpec key = new KeySpec(KeyCode.F24, KeyLocation.STANDARD);
        Map<String, Object> configuration = Map.of("steps", List.of(
                step("keyPress", "key", encoded(key)), step("keyPress", "key", encoded(key)),
                step("keyRelease", "key", encoded(key)),
                step("mouseMove", "position", encoded(ScreenPosition.screen(12, 34))),
                step("mouseWheel", "wheel", encoded(new MouseWheelSpec(WheelDirection.UP, 2))),
                Map.of("action", "emit", "eventType", "macro.completed", "copyInputData", true,
                        "data", Map.of("macro", Map.of("status", "done")))));
        KuudraEvent event = KuudraEvent.of("input", EventData.of("input", Map.of("value", 7)));

        MacroProgram.parse(configuration).execute(event, context(configuration, new MutableSession(), emitted, new AtomicBoolean()), driver);

        assertEquals(List.of("keyPress:" + KeyEvent.VK_F24, "keyRelease:" + KeyEvent.VK_F24,
                "mouseMove:12,34", "mouseWheel:-2"), driver.operations);
        assertEquals(1, emitted.size());
        assertEquals("macro.completed", emitted.get(0).type());
        assertEquals(7, emitted.get(0).data().require("input", "value"));
        assertEquals("done", emitted.get(0).data().require("macro", "status"));
    }

    @Test
    void loopConditionReadsLatestSessionValueAndBreaksNearestLoop() {
        RecordingDriver driver = new RecordingDriver();
        MutableSession session = new MutableSession(); session.put("continue", true);
        AtomicInteger reads = new AtomicInteger();
        session.onSnapshot = () -> { if (reads.incrementAndGet() == 3) session.put("continue", false); };
        Map<String, Object> condition = Map.of("ref", "session#continue", "operator", "TRUTHY");
        Map<String, Object> configuration = Map.of("steps", List.of(Map.of(
                "action", "loop", "maxIterations", 20, "while", condition,
                "steps", List.of(step("keyTap", "key", encoded(new KeySpec(KeyCode.F24, KeyLocation.STANDARD)))))));

        MacroProgram.parse(configuration).execute(KuudraEvent.of("input", Map.of()),
                context(configuration, session, new ArrayList<>(), new AtomicBoolean()), driver);

        assertTrue(driver.operations.size() >= 2);
        assertTrue(driver.operations.size() < 40);
        assertEquals(driver.operations.stream().filter(value -> value.startsWith("keyPress")).count(),
                driver.operations.stream().filter(value -> value.startsWith("keyRelease")).count());
    }

    @Test
    void cancelSessionStopsProgramAndReleasesHeldInput() {
        RecordingDriver driver = new RecordingDriver(); AtomicBoolean cancelled = new AtomicBoolean();
        KeySpec key = new KeySpec(KeyCode.F24, KeyLocation.STANDARD);
        Map<String, Object> configuration = Map.of("steps", List.of(
                step("keyPress", "key", encoded(key)), Map.of("action", "cancelSession", "reason", "guard"),
                step("keyPress", "key", encoded(new KeySpec(KeyCode.F23, KeyLocation.STANDARD)))));

        MacroProgram.parse(configuration).execute(KuudraEvent.of("input", Map.of()),
                context(configuration, new MutableSession(), new ArrayList<>(), cancelled), driver);

        assertTrue(cancelled.get());
        assertEquals(List.of("keyPress:" + KeyEvent.VK_F24, "keyRelease:" + KeyEvent.VK_F24), driver.operations);
    }

    @Test
    void driverFailureStillReleasesPreviouslyPressedKey() {
        RecordingDriver driver = new RecordingDriver(); driver.failMove = true;
        KeySpec key = new KeySpec(KeyCode.F24, KeyLocation.STANDARD);
        Map<String, Object> configuration = Map.of("steps", List.of(
                step("keyPress", "key", encoded(key)), step("mouseMove", "position", encoded(ScreenPosition.screen(1, 2)))));

        assertThrows(RuntimeException.class, () -> MacroProgram.parse(configuration).execute(KuudraEvent.of("input", Map.of()),
                context(configuration, new MutableSession(), new ArrayList<>(), new AtomicBoolean()), driver));
        assertEquals("keyRelease:" + KeyEvent.VK_F24, driver.operations.get(driver.operations.size() - 1));
    }

    @Test
    void pauseReleasesHeldKeyAndResumeReacquiresItBeforeContinuing() throws Exception {
        RecordingDriver driver = new RecordingDriver();
        KeySpec key = new KeySpec(KeyCode.F24, KeyLocation.STANDARD);
        Map<String, Object> configuration = Map.of("steps", List.of(
                step("keyPress", "key", encoded(key)), Map.of("action", "sleep", "durationMillis", 150),
                step("keyRelease", "key", encoded(key))));
        AtomicReference<ExecutionDecision> decision = new AtomicReference<>(ExecutionDecision.CONTINUE);
        CompletableFuture<ExecutionDecision> resumed = new CompletableFuture<>();
        ExecutionControl execution = new ExecutionControl() {
            @Override public ExecutionDecision poll() { return decision.get(); }
            @Override public java.util.concurrent.CompletionStage<ExecutionDecision> checkpoint() { return resumed; }
        };
        EventHandlerContext context = context(configuration, new MutableSession(), new ArrayList<>(), new AtomicBoolean(), execution);
        CompletableFuture<Void> running = CompletableFuture.runAsync(() -> MacroProgram.parse(configuration)
                .execute(KuudraEvent.of("input", Map.of()), context, driver));
        awaitOperations(driver, 1);
        decision.set(ExecutionDecision.PAUSE);
        awaitOperations(driver, 2);
        assertEquals(List.of("keyPress:" + KeyEvent.VK_F24, "keyRelease:" + KeyEvent.VK_F24), List.copyOf(driver.operations));
        decision.set(ExecutionDecision.CONTINUE); resumed.complete(ExecutionDecision.CONTINUE);
        running.get(2, TimeUnit.SECONDS);
        assertEquals(List.of("keyPress:" + KeyEvent.VK_F24, "keyRelease:" + KeyEvent.VK_F24,
                "keyPress:" + KeyEvent.VK_F24, "keyRelease:" + KeyEvent.VK_F24), driver.operations);
    }

    @Test
    void sharedPhysicalDeviceSerializesWorkAcrossComponentInstances() throws Exception {
        RecordingDriver driver = new RecordingDriver();
        SharedRobotDevice device = new SharedRobotDevice(() -> driver);
        AtomicInteger running = new AtomicInteger(); AtomicInteger peak = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        var one = device.submit(() -> {
            peak.accumulateAndGet(running.incrementAndGet(), Math::max); first.countDown(); release.await(); running.decrementAndGet();
        });
        assertTrue(first.await(1, TimeUnit.SECONDS));
        var two = device.submit(() -> { peak.accumulateAndGet(running.incrementAndGet(), Math::max); running.decrementAndGet(); });
        Thread.sleep(50); assertEquals(1, peak.get());
        release.countDown(); CompletableFuture.allOf(one.toCompletableFuture(), two.toCompletableFuture()).get(1, TimeUnit.SECONDS);
        assertEquals(1, peak.get()); device.close();
    }

    private static Map<String, Object> step(String action, String key, Object value) { return Map.of("action", action, key, value); }
    private static Object encoded(Object value) { return ContextCodecs.defaultCodec().encode(value); }
    private static EventHandlerContext context(Map<String, Object> configuration, MutableSession session,
                                         List<KuudraEvent> emitted, AtomicBoolean cancelled) {
        return context(configuration, session, emitted, cancelled, () -> ExecutionDecision.CONTINUE);
    }
    private static EventHandlerContext context(Map<String, Object> configuration, MutableSession session,
                                         List<KuudraEvent> emitted, AtomicBoolean cancelled, ExecutionControl execution) {
        UUID id = UUID.randomUUID();
        CurrentSessionControl control = new CurrentSessionControl() {
            @Override public UUID sessionId() { return id; }
            @Override public boolean requestCancellation(String reason) { return !cancelled.getAndSet(true); }
        };
        MutableValues shared = new MutableValues();
        return new EventHandlerContext() {
            @Override public UUID sessionId() { return id; }
            @Override public String abilityId() { return "test/ability"; }
            @Override public long abilityRevision() { return 1; }
            @Override public String nodeId() { return "macro"; }
            @Override public String handlerName() { return "execute"; }
            @Override public SessionContext session() { return session; }
            @Override public AbilityContext ability() { return shared; }
            @Override public GlobalContext global() { return shared; }
            @Override public TypedValueMap arguments() { return TypedValueMap.of(configuration); }
            @Override public ExecutionControl executionControl() { return execution; }
            @Override public CurrentSessionControl sessionControl() { return control; }
            @Override public boolean emit(KuudraEvent event) { emitted.add(event); return true; }
        };
    }

    private static void awaitOperations(RecordingDriver driver, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (driver.operations.size() < count && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(driver.operations.size() >= count);
    }

    private static final class MutableSession implements SessionContext {
        private final AtomicReference<Map<String, Object>> values = new AtomicReference<>(Map.of());
        Runnable onSnapshot = () -> { };
        @Override public Map<String, Object> snapshot() { Map<String, Object> result = values.get(); onSnapshot.run(); return result; }
        @Override public boolean compareAndSet(Map<String, Object> expected, Map<String, Object> replacement) { return values.compareAndSet(expected, Map.copyOf(replacement)); }
        @Override public Map<String, Object> update(UnaryOperator<Map<String, Object>> operation) { return values.updateAndGet(value -> Map.copyOf(operation.apply(value))); }
    }
    private static final class MutableValues implements AbilityContext, GlobalContext {
        private final AtomicReference<Map<String, Object>> values = new AtomicReference<>(Map.of());
        @Override public Map<String, Object> snapshot() { return values.get(); }
        @Override public boolean compareAndSet(Map<String, Object> expected, Map<String, Object> replacement) {
            return values.compareAndSet(expected, Map.copyOf(replacement));
        }
        @Override public Map<String, Object> update(UnaryOperator<Map<String, Object>> operation) {
            return values.updateAndGet(value -> Map.copyOf(operation.apply(value)));
        }
    }
    private static final class RecordingDriver implements RobotDriver {
        final List<String> operations = java.util.Collections.synchronizedList(new ArrayList<>()); boolean failMove;
        @Override public void keyPress(int code) { operations.add("keyPress:" + code); }
        @Override public void keyRelease(int code) { operations.add("keyRelease:" + code); }
        @Override public void mousePress(int mask) { operations.add("mousePress:" + mask); }
        @Override public void mouseRelease(int mask) { operations.add("mouseRelease:" + mask); }
        @Override public void mouseMove(int x, int y) { operations.add("mouseMove:" + x + "," + y); if (failMove) throw new IllegalStateException("move failed"); }
        @Override public void mouseWheel(int amount) { operations.add("mouseWheel:" + amount); }
    }
}
