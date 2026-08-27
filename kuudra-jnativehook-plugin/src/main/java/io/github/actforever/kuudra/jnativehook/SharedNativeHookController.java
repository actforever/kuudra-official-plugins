package io.github.actforever.kuudra.jnativehook;

import com.github.kwhat.jnativehook.GlobalScreen;

final class SharedNativeHookController implements NativeHookController {
    static final SharedNativeHookController INSTANCE = new SharedNativeHookController();
    private int leases;
    private boolean owned;

    private SharedNativeHookController() { }

    @Override public synchronized void acquire() throws Exception {
        if (leases++ > 0) return;
        try {
            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook();
                owned = true;
            }
        } catch (Exception error) {
            leases = 0;
            owned = false;
            throw error;
        }
    }

    @Override public synchronized void release() throws Exception {
        if (leases == 0 || --leases > 0) return;
        if (owned && GlobalScreen.isNativeHookRegistered()) GlobalScreen.unregisterNativeHook();
        owned = false;
    }
}
