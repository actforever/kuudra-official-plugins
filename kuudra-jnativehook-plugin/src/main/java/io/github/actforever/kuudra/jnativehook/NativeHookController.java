package io.github.actforever.kuudra.jnativehook;

interface NativeHookController {
    void acquire() throws Exception;
    void release() throws Exception;
}
