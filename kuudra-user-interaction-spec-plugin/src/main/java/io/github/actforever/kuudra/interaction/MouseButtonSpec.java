package io.github.actforever.kuudra.interaction;

import java.util.Objects;

public record MouseButtonSpec(MouseButton button) implements InteractionSpec {
    public MouseButtonSpec { Objects.requireNonNull(button, "button"); }
}
