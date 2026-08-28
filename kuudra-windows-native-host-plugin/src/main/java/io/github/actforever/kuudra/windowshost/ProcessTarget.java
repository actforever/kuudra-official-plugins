package io.github.actforever.kuudra.windowshost;

import java.nio.file.Path;

/** One statically authorized executable target exposed to the privileged broker. */
public record ProcessTarget(String alias, Path executablePath) {
    public ProcessTarget {
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("Target alias must not be blank");
        if (executablePath == null || !executablePath.isAbsolute()) {
            throw new IllegalArgumentException("Target executable path must be absolute: " + executablePath);
        }
        executablePath = executablePath.normalize();
    }
}
