package io.github.actforever.kuudra.jnativehook;

import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import io.github.actforever.kuudra.interaction.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

final class NativeEventMapper {
    private static final Map<Integer, KeyCode> KEY_CODES = buildKeyCodes();

    private NativeEventMapper() { }

    static KeySpec key(NativeKeyEvent event) {
        return new KeySpec(KEY_CODES.getOrDefault(event.getKeyCode(), KeyCode.UNKNOWN), switch (event.getKeyLocation()) {
            case NativeKeyEvent.KEY_LOCATION_STANDARD -> KeyLocation.STANDARD;
            case NativeKeyEvent.KEY_LOCATION_LEFT -> KeyLocation.LEFT;
            case NativeKeyEvent.KEY_LOCATION_RIGHT -> KeyLocation.RIGHT;
            case NativeKeyEvent.KEY_LOCATION_NUMPAD -> KeyLocation.NUMPAD;
            default -> KeyLocation.UNKNOWN;
        });
    }

    static MouseButtonSpec button(NativeMouseEvent event) {
        return new MouseButtonSpec(switch (event.getButton()) {
            case NativeMouseEvent.BUTTON1 -> MouseButton.BUTTON_1;
            case NativeMouseEvent.BUTTON2 -> MouseButton.BUTTON_2;
            case NativeMouseEvent.BUTTON3 -> MouseButton.BUTTON_3;
            case NativeMouseEvent.BUTTON4 -> MouseButton.BUTTON_4;
            case NativeMouseEvent.BUTTON5 -> MouseButton.BUTTON_5;
            default -> MouseButton.UNKNOWN;
        });
    }

    static ScreenPosition position(NativeMouseEvent event) { return ScreenPosition.screen(event.getX(), event.getY()); }

    static List<ModifierKey> modifiers(int mask) {
        List<ModifierKey> result = new ArrayList<>();
        add(mask, NativeInputEvent.SHIFT_L_MASK, ModifierKey.SHIFT_LEFT, result);
        add(mask, NativeInputEvent.SHIFT_R_MASK, ModifierKey.SHIFT_RIGHT, result);
        add(mask, NativeInputEvent.CTRL_L_MASK, ModifierKey.CONTROL_LEFT, result);
        add(mask, NativeInputEvent.CTRL_R_MASK, ModifierKey.CONTROL_RIGHT, result);
        add(mask, NativeInputEvent.ALT_L_MASK, ModifierKey.ALT_LEFT, result);
        add(mask, NativeInputEvent.ALT_R_MASK, ModifierKey.ALT_RIGHT, result);
        add(mask, NativeInputEvent.META_L_MASK, ModifierKey.META_LEFT, result);
        add(mask, NativeInputEvent.META_R_MASK, ModifierKey.META_RIGHT, result);
        add(mask, NativeInputEvent.BUTTON1_MASK, ModifierKey.BUTTON_1, result);
        add(mask, NativeInputEvent.BUTTON2_MASK, ModifierKey.BUTTON_2, result);
        add(mask, NativeInputEvent.BUTTON3_MASK, ModifierKey.BUTTON_3, result);
        add(mask, NativeInputEvent.BUTTON4_MASK, ModifierKey.BUTTON_4, result);
        add(mask, NativeInputEvent.BUTTON5_MASK, ModifierKey.BUTTON_5, result);
        add(mask, NativeInputEvent.NUM_LOCK_MASK, ModifierKey.NUM_LOCK, result);
        add(mask, NativeInputEvent.CAPS_LOCK_MASK, ModifierKey.CAPS_LOCK, result);
        add(mask, NativeInputEvent.SCROLL_LOCK_MASK, ModifierKey.SCROLL_LOCK, result);
        return List.copyOf(result);
    }

    private static void add(int mask, int flag, ModifierKey key, List<ModifierKey> result) {
        if ((mask & flag) != 0) result.add(key);
    }

    private static Map<Integer, KeyCode> buildKeyCodes() {
        Map<Integer, KeyCode> result = new HashMap<>();
        for (Field field : NativeKeyEvent.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !field.getName().startsWith("VC_")) continue;
            String name = field.getName().substring(3);
            if (name.length() == 1 && Character.isDigit(name.charAt(0))) name = "NUM_" + name;
            if (name.equals("PRINTSCREEN")) name = "PRINT_SCREEN";
            if (name.equals("UNDEFINED")) name = "UNKNOWN";
            try { result.put(field.getInt(null), KeyCode.valueOf(name)); }
            catch (IllegalAccessException | IllegalArgumentException ignored) { }
        }
        return Map.copyOf(result);
    }
}
