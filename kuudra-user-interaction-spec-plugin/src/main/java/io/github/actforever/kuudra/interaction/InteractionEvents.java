package io.github.actforever.kuudra.interaction;

/** Stable Event types and EventData paths shared by capture and simulation plugins. */
public final class InteractionEvents {
    public static final String DATA_NAMESPACE = "user-interaction";
    public static final String NATIVE_DATA_NAMESPACE = "jnativehook";
    public static final String KEY = "key";
    public static final String BUTTON = "button";
    public static final String WHEEL = "wheel";
    public static final String POSITION = "position";
    public static final String PHASE = "phase";
    public static final String MODIFIERS = "modifiers";

    public static final String KEY_PRESSED = "user-interaction.keyboard.pressed";
    public static final String KEY_RELEASED = "user-interaction.keyboard.released";
    public static final String MOUSE_BUTTON_PRESSED = "user-interaction.mouse-button.pressed";
    public static final String MOUSE_BUTTON_RELEASED = "user-interaction.mouse-button.released";
    public static final String MOUSE_MOVED = "user-interaction.mouse-motion.moved";
    public static final String MOUSE_DRAGGED = "user-interaction.mouse-motion.dragged";
    public static final String MOUSE_WHEEL_SCROLLED = "user-interaction.mouse-wheel.scrolled";

    private InteractionEvents() { }
}
