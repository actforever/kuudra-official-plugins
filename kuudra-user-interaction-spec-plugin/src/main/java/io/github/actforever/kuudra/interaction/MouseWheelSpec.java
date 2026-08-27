package io.github.actforever.kuudra.interaction;

import java.util.Objects;

public record MouseWheelSpec(WheelDirection direction, int amount) implements InteractionSpec {
    public MouseWheelSpec {
        Objects.requireNonNull(direction, "direction");
        if (amount < 1) throw new IllegalArgumentException("amount must be positive");
    }
}
