package io.github.actforever.kuudra.processcontrol;

import io.github.actforever.kuudra.windowshost.ProcessTarget;

import java.nio.file.*;
import java.util.*;

record ProcessControlConfiguration(boolean allowElevation, List<ProcessTarget> targets,
                                   long defaultDurationMillis, long maxDurationMillis) {
    static final long DEFAULT_DURATION_MILLIS = 10_000;
    static final long DEFAULT_MAX_DURATION_MILLIS = 60_000;
    static final long TECHNICAL_MAX_DURATION_MILLIS = 86_400_000;

    static ProcessControlConfiguration decode(Map<String, Object> options) {
        boolean elevation = booleanValue(options.getOrDefault("allowElevation", false), "allowElevation");
        long maximum = number(options.getOrDefault("maxDurationMillis", DEFAULT_MAX_DURATION_MILLIS), "maxDurationMillis");
        long fallback = number(options.getOrDefault("defaultDurationMillis", DEFAULT_DURATION_MILLIS), "defaultDurationMillis");
        if (maximum < 100 || maximum > TECHNICAL_MAX_DURATION_MILLIS) {
            throw new IllegalArgumentException("maxDurationMillis must be between 100 and " + TECHNICAL_MAX_DURATION_MILLIS);
        }
        if (fallback < 100 || fallback > maximum) {
            throw new IllegalArgumentException("defaultDurationMillis must be between 100 and maxDurationMillis");
        }
        Object configuredTargets = options.get("targets");
        if (!(configuredTargets instanceof Map<?, ?> map) || map.isEmpty()) {
            throw new IllegalArgumentException("targets must be a non-empty map");
        }
        List<ProcessTarget> targets = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String alias = String.valueOf(entry.getKey());
            if (!alias.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw new IllegalArgumentException("Invalid target alias: " + alias);
            if (!(entry.getValue() instanceof Map<?, ?> definition)) throw new IllegalArgumentException("targets." + alias + " must be an object");
            Object rawPath = definition.get("executablePath");
            if (!(rawPath instanceof String configured) || configured.isBlank() || configured.contains("${")) {
                throw new IllegalArgumentException("targets." + alias + ".executablePath must be a static absolute path");
            }
            final Path path;
            try { path = Path.of(configured).toAbsolutePath().normalize(); }
            catch (InvalidPathException error) { throw new IllegalArgumentException("Invalid executable path for target " + alias, error); }
            if (!Path.of(configured).isAbsolute()) throw new IllegalArgumentException("Executable path must be absolute for target " + alias);
            if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Executable path is not a regular file for target " + alias + ": " + path);
            targets.add(new ProcessTarget(alias, path));
        }
        return new ProcessControlConfiguration(elevation, List.copyOf(targets), fallback, maximum);
    }

    private static long number(Object value, String path) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(path + " must be a number without placeholders");
        return number.longValue();
    }

    private static boolean booleanValue(Object value, String path) {
        if (!(value instanceof Boolean bool)) throw new IllegalArgumentException(path + " must be a boolean without placeholders");
        return bool;
    }
}
