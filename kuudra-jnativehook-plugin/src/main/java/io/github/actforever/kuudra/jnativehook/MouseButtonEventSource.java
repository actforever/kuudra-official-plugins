package io.github.actforever.kuudra.jnativehook;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseListener;
import io.github.actforever.kuudra.interaction.*;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.EventEmission;
import io.github.actforever.kuudra.plugin.annotation.InstancePolicy;

import java.util.Map;

@io.github.actforever.kuudra.plugin.annotation.EventSource(value = "jnativehook-mouse-button",
        instancePolicy = @InstancePolicy(maxInstances = 1, exclusivityDomain = "actforever/jnativehook-mouse-button", threadSafe = true))
@ComponentDoc(purpose = "Captures global mouse-button press and release events without synthesizing click gestures.",
        lifecyclePhases = {"start: attach mouse-button listener", "pause: detach listener", "resume: reattach listener", "stop: release listener and native hook lease"},
        emittedEvents = {
                @EventEmission(stage = "native mouse pressed", eventType = InteractionEvents.MOUSE_BUTTON_PRESSED,
                        dataExample = "{\"user-interaction\":{\"button\":{\"button\":\"BUTTON_1\"},\"position\":{\"x\":10,\"y\":20,\"coordinateSpace\":\"SCREEN\"},\"phase\":\"PRESSED\"}}"),
                @EventEmission(stage = "native mouse released", eventType = InteractionEvents.MOUSE_BUTTON_RELEASED)
        })
public final class MouseButtonEventSource extends AbstractNativeEventSource implements NativeMouseListener {
    @Override public void nativeMouseClicked(NativeMouseEvent event) { }
    @Override public void nativeMousePressed(NativeMouseEvent event) { emit(event, InteractionPhase.PRESSED, InteractionEvents.MOUSE_BUTTON_PRESSED); }
    @Override public void nativeMouseReleased(NativeMouseEvent event) { emit(event, InteractionPhase.RELEASED, InteractionEvents.MOUSE_BUTTON_RELEASED); }

    private void emit(NativeMouseEvent event, InteractionPhase phase, String type) {
        emitSafely(NativeKuudraEvents.event(type, Map.of(
                        InteractionEvents.BUTTON, NativeEventMapper.button(event),
                        InteractionEvents.POSITION, NativeEventMapper.position(event),
                        InteractionEvents.PHASE, phase,
                        InteractionEvents.MODIFIERS, NativeEventMapper.modifiers(event.getModifiers())), event,
                Map.of("button", event.getButton(), "clickCount", event.getClickCount(), "x", event.getX(), "y", event.getY())));
    }

    @Override protected String componentName() { return "jnativehook-mouse-button"; }
    @Override protected void attachListener() { GlobalScreen.addNativeMouseListener(this); }
    @Override protected void detachListener() { GlobalScreen.removeNativeMouseListener(this); }
}
