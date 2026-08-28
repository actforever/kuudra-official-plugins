package io.github.actforever.kuudra.jnativehook;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.interaction.*;

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

    @Test void dropsMatchingInjectedInteractionAndMarksPhysicalInput() {
        FakeSource source = new FakeSource(new FakeController());
        AtomicInteger emitted = new AtomicInteger();
        source.setEmitter(event -> {
            assertEquals(false, event.data().require(InteractionEvents.DATA_NAMESPACE, InteractionEvents.SYNTHETIC));
            emitted.incrementAndGet(); return true;
        });
        source.start().toCompletableFuture().join();
        KeySpec key = new KeySpec(KeyCode.F24, KeyLocation.STANDARD);
        InteractionSignature signature = new InteractionSignature(InteractionEvents.KEY_PRESSED, key);
        try (InjectedInteractionRegistry.Ticket ticket = InjectedInteractionRegistry.global().expect(signature, Duration.ofSeconds(1))) {
            ticket.commit();
        }
        source.publish(signature);
        assertEquals(0, emitted.get());
        source.publish(signature);
        assertEquals(1, emitted.get());
        source.stop().toCompletableFuture().join();
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
        private void publish(InteractionSignature signature) {
            emitSafely(KuudraEvent.of(signature.eventType(), java.util.Map.of()), signature);
        }
    }
}
