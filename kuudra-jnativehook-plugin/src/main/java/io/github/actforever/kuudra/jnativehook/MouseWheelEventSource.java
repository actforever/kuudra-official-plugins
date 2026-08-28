package io.github.actforever.kuudra.jnativehook;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelListener;
import io.github.actforever.kuudra.interaction.*;
import io.github.actforever.kuudra.plugin.annotation.ComponentDoc;
import io.github.actforever.kuudra.plugin.annotation.EventEmission;
import io.github.actforever.kuudra.plugin.annotation.InstancePolicy;
import io.github.actforever.kuudra.plugin.annotation.SpecProperty;

import java.util.Map;

@io.github.actforever.kuudra.plugin.annotation.EventSource(value = "jnativehook-mouse-wheel",
        instancePolicy = @InstancePolicy(maxInstances = 1, exclusivityDomain = "actforever/jnativehook-mouse-wheel", threadSafe = true))
@ComponentDoc(purpose = "Captures global mouse-wheel events as platform-neutral direction and amount values.",
        lifecyclePhases = {"start: attach wheel listener", "pause: detach listener", "resume: reattach listener", "stop: release listener and native hook lease"},
        configuration = @SpecProperty(path = "syntheticEventPolicy", type = String.class, defaultValue = "\"DROP\"",
                allowedValues = {"DROP", "EMIT"}, description = "Drop matching in-process simulated input or emit it with synthetic=true."),
        emittedEvents = @EventEmission(stage = "native mouse wheel", eventType = InteractionEvents.MOUSE_WHEEL_SCROLLED,
                dataExample = "{\"user-interaction\":{\"wheel\":{\"direction\":\"DOWN\",\"amount\":1},\"position\":{\"x\":10,\"y\":20,\"coordinateSpace\":\"SCREEN\"},\"phase\":\"SCROLLED\"}}"))
public final class MouseWheelEventSource extends AbstractNativeEventSource implements NativeMouseWheelListener {
    @Override public void nativeMouseWheelMoved(NativeMouseWheelEvent event) {
        int rotation = event.getWheelRotation();
        MouseWheelSpec wheel = new MouseWheelSpec(rotation < 0 ? WheelDirection.UP : WheelDirection.DOWN,
                Math.max(1, Math.abs(rotation)));
        emitSafely(NativeKuudraEvents.event(InteractionEvents.MOUSE_WHEEL_SCROLLED, Map.of(
                        InteractionEvents.WHEEL, wheel,
                        InteractionEvents.POSITION, NativeEventMapper.position(event),
                        InteractionEvents.PHASE, InteractionPhase.SCROLLED,
                        InteractionEvents.MODIFIERS, NativeEventMapper.modifiers(event.getModifiers())), event,
                Map.of("wheelRotation", rotation, "scrollAmount", event.getScrollAmount(),
                        "scrollType", event.getScrollType(), "wheelDirection", event.getWheelDirection(),
                        "x", event.getX(), "y", event.getY())),
                new InteractionSignature(InteractionEvents.MOUSE_WHEEL_SCROLLED, wheel));
    }

    @Override protected String componentName() { return "jnativehook-mouse-wheel"; }
    @Override protected void attachListener() { GlobalScreen.addNativeMouseWheelListener(this); }
    @Override protected void detachListener() { GlobalScreen.removeNativeMouseWheelListener(this); }
}
