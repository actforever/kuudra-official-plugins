package io.github.actforever.kuudra.jnativehook;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeSourceLifecycleTest {
    @Test void lifecycleIsIdempotentAndPauseKeepsLease() {
        FakeController controller = new FakeController();
        FakeSource source = new FakeSource(controller);
        source.setEmitter(event -> true);

        source.start().toCompletableFuture().join();
        source.start().toCompletableFuture().join();
        source.pause().toCompletableFuture().join();
        source.pause().toCompletableFuture().join();
        source.resume().toCompletableFuture().join();
        source.stop().toCompletableFuture().join();
        source.stop().toCompletableFuture().join();

        assertEquals(1, controller.acquired.get());
        assertEquals(1, controller.released.get());
        assertEquals(2, source.attached.get());
        assertEquals(2, source.detached.get());
    }

    private static final class FakeController implements NativeHookController {
        private final AtomicInteger acquired = new AtomicInteger();
        private final AtomicInteger released = new AtomicInteger();
        @Override public void acquire() { acquired.incrementAndGet(); }
        @Override public void release() { released.incrementAndGet(); }
    }

    private static final class FakeSource extends AbstractNativeEventSource {
        private final AtomicInteger attached = new AtomicInteger();
        private final AtomicInteger detached = new AtomicInteger();
        private FakeSource(NativeHookController controller) { super(controller); }
        @Override protected String componentName() { return "fake"; }
        @Override protected void attachListener() { attached.incrementAndGet(); }
        @Override protected void detachListener() { detached.incrementAndGet(); }
    }
}
