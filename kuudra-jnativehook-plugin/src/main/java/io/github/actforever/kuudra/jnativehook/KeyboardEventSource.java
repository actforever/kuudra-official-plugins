package io.github.actforever.kuudra.jnativehook;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import io.github.actforever.kuudra.interaction.*;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.EventEmission;
import io.github.actforever.kuudra.plugin.annotation.InstancePolicy;

import java.util.Map;

@io.github.actforever.kuudra.plugin.annotation.EventSource(value = "jnativehook-keyboard",
        instancePolicy = @InstancePolicy(maxInstances = 1, exclusivityDomain = "actforever/jnativehook-keyboard", threadSafe = true))
@ComponentDoc(purpose = "Captures global keyboard press and release events and emits platform-neutral logical keys.",
        lifecyclePhases = {"start: acquire the shared native hook and attach the keyboard listener",
                "pause: detach the listener while preserving the native hook lease",
                "resume: reattach the listener", "stop: detach and release the native hook lease"},
        emittedEvents = {
                @EventEmission(stage = "native key pressed", eventType = InteractionEvents.KEY_PRESSED,
                        description = "Emits key, phase and modifier data.",
                        dataExample = "{\"user-interaction\":{\"key\":{\"code\":\"A\",\"location\":\"STANDARD\"},\"phase\":\"PRESSED\",\"modifiers\":[]}}"),
                @EventEmission(stage = "native key released", eventType = InteractionEvents.KEY_RELEASED,
                        description = "Emits key, phase and modifier data.")
        })
public final class KeyboardEventSource extends AbstractNativeEventSource implements NativeKeyListener {
    @Override public void nativeKeyTyped(NativeKeyEvent event) { }
    @Override public void nativeKeyPressed(NativeKeyEvent event) { emit(event, InteractionPhase.PRESSED, InteractionEvents.KEY_PRESSED); }
    @Override public void nativeKeyReleased(NativeKeyEvent event) { emit(event, InteractionPhase.RELEASED, InteractionEvents.KEY_RELEASED); }

    private void emit(NativeKeyEvent event, InteractionPhase phase, String type) {
        emitSafely(NativeKuudraEvents.event(type, Map.of(
                        InteractionEvents.KEY, NativeEventMapper.key(event),
                        InteractionEvents.PHASE, phase,
                        InteractionEvents.MODIFIERS, NativeEventMapper.modifiers(event.getModifiers())), event,
                Map.of("keyCode", event.getKeyCode(), "rawCode", event.getRawCode(),
                        "keyLocation", event.getKeyLocation(), "keyText", NativeKeyEvent.getKeyText(event.getKeyCode()))));
    }

    @Override protected String componentName() { return "jnativehook-keyboard"; }
    @Override protected void attachListener() { GlobalScreen.addNativeKeyListener(this); }
    @Override protected void detachListener() { GlobalScreen.removeNativeKeyListener(this); }
}
