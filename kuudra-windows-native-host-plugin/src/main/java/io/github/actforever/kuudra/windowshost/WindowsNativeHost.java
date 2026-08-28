package io.github.actforever.kuudra.windowshost;

import java.util.Collection;

/** Shared service exported by actforever/windows-native-host to its declared dependents. */
public final class WindowsNativeHost {
    interface Provider extends AutoCloseable {
        ProcessControlLease acquire(String owner, boolean allowElevation, Collection<ProcessTarget> targets, long maxDurationMillis);
        @Override void close();
    }

    private static volatile Provider provider;

    private WindowsNativeHost() { }

    public static ProcessControlLease acquireProcessControl(String owner, boolean allowElevation,
                                                            Collection<ProcessTarget> targets, long maxDurationMillis) {
        Provider current = provider;
        if (current == null) throw new NativeHostException("HOST_UNAVAILABLE", "Windows native host plugin is not active");
        return current.acquire(owner, allowElevation, targets, maxDurationMillis);
    }

    static synchronized void install(Provider next) {
        if (provider != null) throw new IllegalStateException("Windows native host is already installed");
        provider = next;
    }

    static synchronized void uninstall(Provider expected) {
        if (provider == expected) provider = null;
    }
}
