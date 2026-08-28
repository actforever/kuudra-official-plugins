package io.github.actforever.kuudra.awtrobot;

import java.util.concurrent.CompletionStage;

interface RobotDevice {
    CompletionStage<Void> submit(ThrowingTask task);
    RobotDriver driver();
    default void close() { }

    @FunctionalInterface interface ThrowingTask { void run() throws Exception; }
}
