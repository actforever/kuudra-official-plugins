package io.github.actforever.kuudra.jnativehook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MotionOutputOptionsTest {
    @Test void validatesLimitedStrategies() {
        assertEquals(16, MotionOutputOptions.defaults().intervalMillis());
        assertThrows(IllegalArgumentException.class, () -> new MotionOutputOptions(MotionOutputStrategy.COALESCE, 0));
        assertDoesNotThrow(() -> new MotionOutputOptions(MotionOutputStrategy.UNLIMITED, 0));
    }
}
