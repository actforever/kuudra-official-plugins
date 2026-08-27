package io.github.actforever.kuudra.interaction;

/** Marker for platform-neutral user-interaction values transported as JSON-compatible data. */
public sealed interface InteractionSpec permits KeySpec, MouseButtonSpec, MouseWheelSpec, ScreenPosition { }
