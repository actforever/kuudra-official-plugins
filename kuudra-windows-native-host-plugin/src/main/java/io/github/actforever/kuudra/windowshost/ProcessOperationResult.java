package io.github.actforever.kuudra.windowshost;

import java.time.Instant;
import java.util.UUID;

public record ProcessOperationResult(UUID operationId, String target, long pid, ProcessOperationOutcome outcome,
                                     Instant startedAt, Instant completedAt) { }
