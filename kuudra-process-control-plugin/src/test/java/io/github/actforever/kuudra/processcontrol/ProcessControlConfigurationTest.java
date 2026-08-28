package io.github.actforever.kuudra.processcontrol;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProcessControlConfigurationTest {
    @Test void decodesStaticAllowlistAndDurationDefaults() {
        String executable = ProcessHandle.current().info().command().orElseThrow();
        ProcessControlConfiguration configuration = ProcessControlConfiguration.decode(Map.of(
                "allowElevation", true,
                "targets", Map.of("java", Map.of("executablePath", executable))));
        assertTrue(configuration.allowElevation());
        assertEquals(10_000, configuration.defaultDurationMillis());
        assertEquals(60_000, configuration.maxDurationMillis());
        assertEquals(Path.of(executable).toAbsolutePath().normalize(), configuration.targets().get(0).executablePath());
    }

    @Test void rejectsPlaceholdersAndUnsafeDurationRanges() {
        assertThrows(IllegalArgumentException.class, () -> ProcessControlConfiguration.decode(Map.of(
                "targets", Map.of("dynamic", Map.of("executablePath", "${event#path}")))));
        String executable = ProcessHandle.current().info().command().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> ProcessControlConfiguration.decode(Map.of(
                "targets", Map.of("java", Map.of("executablePath", executable)),
                "defaultDurationMillis", 2_000, "maxDurationMillis", 1_000)));
    }
}
