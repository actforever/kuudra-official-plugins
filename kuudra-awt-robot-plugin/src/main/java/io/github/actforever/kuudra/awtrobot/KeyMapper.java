package io.github.actforever.kuudra.awtrobot;

import io.github.actforever.kuudra.api.KuudraException;
import io.github.actforever.kuudra.interaction.*;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

final class KeyMapper {
    private KeyMapper() { }
    static int key(KeySpec key) {
        if (key.code() == KeyCode.UNKNOWN) throw new KuudraException("AWT Robot cannot execute UNKNOWN key");
        String name = switch (key.code()) {
            case BACKQUOTE -> "BACK_QUOTE";
            case BACK_SLASH -> "BACK_SLASH";
            case OPEN_BRACKET -> "OPEN_BRACKET";
            case CLOSE_BRACKET -> "CLOSE_BRACKET";
            case CAPS_LOCK -> "CAPS_LOCK";
            case PRINT_SCREEN -> "PRINTSCREEN";
            case PAGE_UP -> "PAGE_UP";
            case PAGE_DOWN -> "PAGE_DOWN";
            case CONTEXT_MENU -> "CONTEXT_MENU";
            default -> key.code().name();
        };
        if (key.code().name().startsWith("NUM_") && key.location() != KeyLocation.NUMPAD)
            name = key.code().name().substring(4);
        else if (key.code().name().startsWith("NUM_") && key.location() == KeyLocation.NUMPAD)
            name = "NUMPAD" + key.code().name().substring(4);
        try { return KeyEvent.class.getField("VK_" + name).getInt(null); }
        catch (ReflectiveOperationException error) { throw new KuudraException("AWT Robot does not support logical key " + key, error); }
    }
    static int mouse(MouseButtonSpec button) {
        int number = switch (button.button()) {
            case BUTTON_1 -> 1; case BUTTON_2 -> 2; case BUTTON_3 -> 3; case BUTTON_4 -> 4; case BUTTON_5 -> 5;
            case UNKNOWN -> throw new KuudraException("AWT Robot cannot execute UNKNOWN mouse button");
        };
        try { return InputEvent.getMaskForButton(number); }
        catch (IllegalArgumentException error) { throw new KuudraException("Mouse button is unavailable: " + button.button(), error); }
    }
    static KeySpec modifier(ModifierKey modifier) {
        return switch (modifier) {
            case SHIFT, SHIFT_LEFT -> new KeySpec(KeyCode.SHIFT, KeyLocation.LEFT);
            case SHIFT_RIGHT -> new KeySpec(KeyCode.SHIFT, KeyLocation.RIGHT);
            case CONTROL, CONTROL_LEFT -> new KeySpec(KeyCode.CONTROL, KeyLocation.LEFT);
            case CONTROL_RIGHT -> new KeySpec(KeyCode.CONTROL, KeyLocation.RIGHT);
            case ALT, ALT_LEFT -> new KeySpec(KeyCode.ALT, KeyLocation.LEFT);
            case ALT_RIGHT -> new KeySpec(KeyCode.ALT, KeyLocation.RIGHT);
            case META, META_LEFT -> new KeySpec(KeyCode.META, KeyLocation.LEFT);
            case META_RIGHT -> new KeySpec(KeyCode.META, KeyLocation.RIGHT);
            case NUM_LOCK -> new KeySpec(KeyCode.NUM_LOCK, KeyLocation.STANDARD);
            case CAPS_LOCK -> new KeySpec(KeyCode.CAPS_LOCK, KeyLocation.STANDARD);
            case SCROLL_LOCK -> new KeySpec(KeyCode.SCROLL_LOCK, KeyLocation.STANDARD);
            default -> throw new KuudraException("Mouse-button modifier is not valid for keyboard type: " + modifier);
        };
    }
}
