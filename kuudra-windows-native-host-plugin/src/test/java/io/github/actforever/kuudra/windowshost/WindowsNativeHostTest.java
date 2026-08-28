package io.github.actforever.kuudra.windowshost;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class WindowsNativeHostTest {
    @Test void exportsTheSingleProviderToDeclaredDependents() {
        AtomicBoolean acquired = new AtomicBoolean();
        WindowsNativeHost.Provider provider = new WindowsNativeHost.Provider() {
            @Override public ProcessControlLease acquire(String owner, boolean allowElevation,
                    Collection<ProcessTarget> targets, long maxDurationMillis) {
                acquired.set(true);
                return noOpLease();
            }
            @Override public void close() { }
        };
        WindowsNativeHost.install(provider);
        try (ProcessControlLease ignored = WindowsNativeHost.acquireProcessControl("component", true,
                java.util.List.of(new ProcessTarget("test", Path.of("C:\\test.exe"))), 1_000)) {
            assertTrue(acquired.get());
        } finally {
            WindowsNativeHost.uninstall(provider);
        }
    }

    @Test void rejectsCallsWhenTheParentPluginIsInactive() {
        NativeHostException error = assertThrows(NativeHostException.class, () ->
                WindowsNativeHost.acquireProcessControl("component", true,
                        java.util.List.of(new ProcessTarget("test", Path.of("C:\\test.exe"))), 1_000));
        assertEquals("HOST_UNAVAILABLE", error.code());
    }

    private static ProcessControlLease noOpLease() {
        return new ProcessControlLease() {
            @Override public CompletionStage<ProcessOperation> suspend(String target, Long pid, Duration duration) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
            @Override public CompletionStage<Void> resume(String target, Long pid) { return CompletableFuture.completedFuture(null); }
            @Override public CompletionStage<Void> restoreAll() { return CompletableFuture.completedFuture(null); }
            @Override public void close() { }
        };
    }
}
