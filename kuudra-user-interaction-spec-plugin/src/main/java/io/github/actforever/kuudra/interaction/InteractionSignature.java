package io.github.actforever.kuudra.interaction;

import java.util.Objects;

/** Platform-neutral identity used to correlate an injected input with a captured native event. */
public record InteractionSignature(String eventType, InteractionSpec value) {
    public InteractionSignature {
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType must not be blank");
        Objects.requireNonNull(value, "value");
    }
}
