package io.github.actforever.kuudra.interaction;

import java.util.Objects;

public record ScreenPosition(int x, int y, CoordinateSpace coordinateSpace) implements InteractionSpec {
    public ScreenPosition { Objects.requireNonNull(coordinateSpace, "coordinateSpace"); }
    public static ScreenPosition screen(int x, int y) { return new ScreenPosition(x, y, CoordinateSpace.SCREEN); }
}
