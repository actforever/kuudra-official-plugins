package io.github.actforever.kuudra.windowshost;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/** Owner-scoped, strongly typed access to process suspension. */
public interface ProcessControlLease extends AutoCloseable {
    CompletionStage<ProcessOperation> suspend(String target, Long pid, Duration duration);
    CompletionStage<Void> resume(String target, Long pid);
    CompletionStage<Void> restoreAll();
    @Override void close();
}
