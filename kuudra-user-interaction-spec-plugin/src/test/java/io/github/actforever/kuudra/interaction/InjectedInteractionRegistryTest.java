package io.github.actforever.kuudra.interaction;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class InjectedInteractionRegistryTest {
    @Test
    void committedExpectationIsConsumedExactlyOnce() {
        InjectedInteractionRegistry registry = new InjectedInteractionRegistry();
        InteractionSignature signature = new InteractionSignature(InteractionEvents.KEY_PRESSED,
                new KeySpec(KeyCode.F24, KeyLocation.STANDARD));
        try (InjectedInteractionRegistry.Ticket ticket = registry.expect(signature, Duration.ofSeconds(1))) {
            ticket.commit();
        }
        assertTrue(registry.consume(signature));
        assertFalse(registry.consume(signature));
    }

    @Test
    void uncommittedExpectationIsRolledBack() {
        InjectedInteractionRegistry registry = new InjectedInteractionRegistry();
        InteractionSignature signature = new InteractionSignature(InteractionEvents.MOUSE_WHEEL_SCROLLED,
                new MouseWheelSpec(WheelDirection.UP, 1));
        try (InjectedInteractionRegistry.Ticket ignored = registry.expect(signature, Duration.ofSeconds(1))) { }
        assertFalse(registry.consume(signature));
    }
}
