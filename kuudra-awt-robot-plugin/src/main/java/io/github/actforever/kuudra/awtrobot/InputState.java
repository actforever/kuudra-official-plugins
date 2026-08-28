package io.github.actforever.kuudra.awtrobot;

import io.github.actforever.kuudra.interaction.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class InputState {
    private final RobotDriver driver;
    private final Duration markerLifetime;
    private final LinkedHashMap<KeySpec, Integer> keys = new LinkedHashMap<>();
    private final LinkedHashMap<MouseButtonSpec, Integer> buttons = new LinkedHashMap<>();
    private boolean physicallyHeld = true;

    InputState(RobotDriver driver, long markerLifetimeMillis) {
        this.driver = driver; this.markerLifetime = Duration.ofMillis(markerLifetimeMillis);
    }

    void keyPress(KeySpec key) {
        if (keys.containsKey(key)) return;
        int code = KeyMapper.key(key); inject(new InteractionSignature(InteractionEvents.KEY_PRESSED, key), () -> driver.keyPress(code));
        keys.put(key, code);
    }
    void keyRelease(KeySpec key) {
        Integer code = keys.remove(key); if (code == null) return;
        inject(new InteractionSignature(InteractionEvents.KEY_RELEASED, key), () -> driver.keyRelease(code));
    }
    void mousePress(MouseButtonSpec button) {
        if (buttons.containsKey(button)) return;
        int mask = KeyMapper.mouse(button); inject(new InteractionSignature(InteractionEvents.MOUSE_BUTTON_PRESSED, button), () -> driver.mousePress(mask));
        buttons.put(button, mask);
    }
    void mouseRelease(MouseButtonSpec button) {
        Integer mask = buttons.remove(button); if (mask == null) return;
        inject(new InteractionSignature(InteractionEvents.MOUSE_BUTTON_RELEASED, button), () -> driver.mouseRelease(mask));
    }
    void mouseMove(ScreenPosition position) {
        if (position.coordinateSpace() != CoordinateSpace.SCREEN) throw new IllegalArgumentException("AWT Robot only supports SCREEN coordinates");
        String type = buttons.isEmpty() ? InteractionEvents.MOUSE_MOVED : InteractionEvents.MOUSE_DRAGGED;
        inject(new InteractionSignature(type, position), () -> driver.mouseMove(position.x(), position.y()));
    }
    void mouseWheel(MouseWheelSpec wheel) {
        int amount = wheel.direction() == WheelDirection.UP ? -wheel.amount() : wheel.amount();
        inject(new InteractionSignature(InteractionEvents.MOUSE_WHEEL_SCROLLED, wheel), () -> driver.mouseWheel(amount));
    }

    void suspend() {
        if (!physicallyHeld) return;
        reverseButtons(false); reverseKeys(false); physicallyHeld = false;
    }
    void resume() {
        if (physicallyHeld) return;
        for (Map.Entry<KeySpec, Integer> entry : keys.entrySet())
            inject(new InteractionSignature(InteractionEvents.KEY_PRESSED, entry.getKey()), () -> driver.keyPress(entry.getValue()));
        for (Map.Entry<MouseButtonSpec, Integer> entry : buttons.entrySet())
            inject(new InteractionSignature(InteractionEvents.MOUSE_BUTTON_PRESSED, entry.getKey()), () -> driver.mousePress(entry.getValue()));
        physicallyHeld = true;
    }
    void releaseAll() { if (physicallyHeld) { reverseButtons(true); reverseKeys(true); } buttons.clear(); keys.clear(); physicallyHeld = true; }
    private void reverseButtons(boolean remove) {
        var entries = new java.util.ArrayList<>(buttons.entrySet()); java.util.Collections.reverse(entries);
        for (var entry : entries) inject(new InteractionSignature(InteractionEvents.MOUSE_BUTTON_RELEASED, entry.getKey()), () -> driver.mouseRelease(entry.getValue()));
        if (remove) buttons.clear();
    }
    private void reverseKeys(boolean remove) {
        var entries = new java.util.ArrayList<>(keys.entrySet()); java.util.Collections.reverse(entries);
        for (var entry : entries) inject(new InteractionSignature(InteractionEvents.KEY_RELEASED, entry.getKey()), () -> driver.keyRelease(entry.getValue()));
        if (remove) keys.clear();
    }
    private void inject(InteractionSignature signature, Runnable operation) {
        try (InjectedInteractionRegistry.Ticket ticket = InjectedInteractionRegistry.global().expect(signature, markerLifetime)) {
            operation.run(); ticket.commit();
        }
    }
}
