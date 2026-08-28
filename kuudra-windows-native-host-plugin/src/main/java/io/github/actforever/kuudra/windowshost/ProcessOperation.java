package io.github.actforever.kuudra.windowshost;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public record ProcessOperation(UUID id, long pid, Instant deadline, CompletionStage<ProcessOperationResult> completion) { }
