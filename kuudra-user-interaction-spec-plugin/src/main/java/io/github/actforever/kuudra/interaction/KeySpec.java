package io.github.actforever.kuudra.interaction;

import java.util.Objects;

/** A logical key independent from the native input provider. */
public record KeySpec(KeyCode code, KeyLocation location) implements InteractionSpec {
    public KeySpec {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(location, "location");
    }
}
