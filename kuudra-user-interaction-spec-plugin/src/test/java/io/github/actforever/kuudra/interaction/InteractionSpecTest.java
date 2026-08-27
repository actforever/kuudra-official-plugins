package io.github.actforever.kuudra.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InteractionSpecTest {
    @Test void validatesRequiredValues() {
        assertEquals(KeyCode.A, new KeySpec(KeyCode.A, KeyLocation.STANDARD).code());
        assertEquals(ScreenPosition.screen(12, 34), new ScreenPosition(12, 34, CoordinateSpace.SCREEN));
        assertThrows(IllegalArgumentException.class, () -> new MouseWheelSpec(WheelDirection.UP, 0));
    }

    @Test void exposesStableEventPaths() {
        assertEquals("user-interaction", InteractionEvents.DATA_NAMESPACE);
        assertEquals("user-interaction.keyboard.pressed", InteractionEvents.KEY_PRESSED);
    }

    @Test void decodesYamlCompatibleObjectsIntoContractTypes() {
        Object yamlValue = java.util.Map.of("code", "A", "location", "STANDARD");
        KeySpec decoded = io.github.actforever.kuudra.api.context.ContextCodecs.defaultCodec()
                .decode(yamlValue, KeySpec.class);
        assertEquals(new KeySpec(KeyCode.A, KeyLocation.STANDARD), decoded);
    }
}
