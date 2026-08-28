package io.github.actforever.kuudra.macro;

import java.util.List;

public record MacroProgramDefinition(List<MacroStep> steps, long maxTotalSteps, long syntheticMarkerLifetimeMillis) {
    public MacroProgramDefinition {
        steps = List.copyOf(steps);
        if (maxTotalSteps < 1 || maxTotalSteps > 1_000_000) throw new IllegalArgumentException("maxTotalSteps must be between 1 and 1000000");
        if (syntheticMarkerLifetimeMillis < 1 || syntheticMarkerLifetimeMillis > 60_000) throw new IllegalArgumentException("syntheticMarkerLifetimeMillis must be between 1 and 60000");
    }
}
