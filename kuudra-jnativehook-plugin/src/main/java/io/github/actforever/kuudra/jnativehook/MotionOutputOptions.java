package io.github.actforever.kuudra.jnativehook;

import java.util.Objects;

public record MotionOutputOptions(MotionOutputStrategy strategy, long intervalMillis) {
    public MotionOutputOptions {
        Objects.requireNonNull(strategy, "strategy");
        if (strategy != MotionOutputStrategy.UNLIMITED && intervalMillis < 1) {
            throw new IllegalArgumentException("intervalMillis must be positive for a limited output strategy");
        }
    }

    public static MotionOutputOptions defaults() { return new MotionOutputOptions(MotionOutputStrategy.COALESCE, 16); }
}
