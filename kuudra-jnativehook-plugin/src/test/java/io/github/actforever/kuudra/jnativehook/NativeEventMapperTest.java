package io.github.actforever.kuudra.jnativehook;

import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import io.github.actforever.kuudra.interaction.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeEventMapperTest {
    @Test void mapsLogicalKeyLocationAndModifiers() {
        NativeKeyEvent event = new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED,
                NativeInputEvent.SHIFT_L_MASK | NativeInputEvent.CTRL_R_MASK,
                30, NativeKeyEvent.VC_A, 'a', NativeKeyEvent.KEY_LOCATION_STANDARD);
        assertEquals(new KeySpec(KeyCode.A, KeyLocation.STANDARD), NativeEventMapper.key(event));
        assertEquals(java.util.List.of(ModifierKey.SHIFT_LEFT, ModifierKey.CONTROL_RIGHT),
                NativeEventMapper.modifiers(event.getModifiers()));
    }

    @Test void preservesUnknownKeysAndButtons() {
        NativeKeyEvent key = new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, 0, 999, 999, NativeKeyEvent.CHAR_UNDEFINED);
        NativeMouseEvent mouse = new NativeMouseEvent(NativeMouseEvent.NATIVE_MOUSE_PRESSED, 0, 10, 20, 99);
        assertEquals(KeyCode.UNKNOWN, NativeEventMapper.key(key).code());
        assertEquals(MouseButton.UNKNOWN, NativeEventMapper.button(mouse).button());
        assertEquals(ScreenPosition.screen(10, 20), NativeEventMapper.position(mouse));
    }

    @Test void eventDataRestoresTheSharedContractType() {
        NativeKeyEvent nativeEvent = new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, 0,
                30, NativeKeyEvent.VC_A, 'a', NativeKeyEvent.KEY_LOCATION_STANDARD);
        var event = NativeKuudraEvents.event(InteractionEvents.KEY_PRESSED,
                java.util.Map.of(InteractionEvents.KEY, NativeEventMapper.key(nativeEvent)), nativeEvent,
                java.util.Map.of("keyCode", nativeEvent.getKeyCode()));
        assertEquals(new KeySpec(KeyCode.A, KeyLocation.STANDARD),
                event.data().get(InteractionEvents.DATA_NAMESPACE, InteractionEvents.KEY, KeySpec.class));
        assertEquals(NativeKeyEvent.VC_A,
                event.data().get(InteractionEvents.NATIVE_DATA_NAMESPACE, "keyCode", Integer.class));
    }
}
