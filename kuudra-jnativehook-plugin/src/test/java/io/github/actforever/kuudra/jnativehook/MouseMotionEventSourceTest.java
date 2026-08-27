package io.github.actforever.kuudra.jnativehook;

import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.interaction.InteractionEvents;
import io.github.actforever.kuudra.interaction.ScreenPosition;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class MouseMotionEventSourceTest {
    @Test void unlimitedEmitsEveryEvent() {
        Queue<KuudraEvent> events = new ConcurrentLinkedQueue<>();
        MouseMotionEventSource source = source(MotionOutputStrategy.UNLIMITED, 0, events);
        source.start().toCompletableFuture().join();
        source.nativeMouseMoved(event(1, 2));
        source.nativeMouseMoved(event(3, 4));
        source.stop().toCompletableFuture().join();
        assertEquals(2, events.size());
    }

    @Test void throttleKeepsOnlyTheLeadingPositionInAWindow() {
        Queue<KuudraEvent> events = new ConcurrentLinkedQueue<>();
        MouseMotionEventSource source = source(MotionOutputStrategy.THROTTLE, 100, events);
        source.start().toCompletableFuture().join();
        source.nativeMouseMoved(event(1, 2));
        source.nativeMouseMoved(event(3, 4));
        source.stop().toCompletableFuture().join();
        assertEquals(1, events.size());
        assertEquals(ScreenPosition.screen(1, 2), position(events.peek()));
    }

    @Test void coalesceEmitsTheLeadingAndLatestPosition() throws Exception {
        Queue<KuudraEvent> events = new ConcurrentLinkedQueue<>();
        // Keep the window above one-time codec/ClassLoader warm-up costs on slow CI hosts.
        MouseMotionEventSource source = source(MotionOutputStrategy.COALESCE, 1_000, events);
        source.start().toCompletableFuture().join();
        source.nativeMouseMoved(event(1, 2));
        source.nativeMouseMoved(event(3, 4));
        source.nativeMouseMoved(event(5, 6));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (events.size() < 2 && System.nanoTime() < deadline) Thread.sleep(5);
        source.stop().toCompletableFuture().join();
        assertEquals(2, events.size());
        assertEquals(ScreenPosition.screen(5, 6), position(events.stream().skip(1).findFirst().orElseThrow()));
    }

    @Test void pauseDropsBufferedTrailingPosition() throws Exception {
        Queue<KuudraEvent> events = new ConcurrentLinkedQueue<>();
        MouseMotionEventSource source = source(MotionOutputStrategy.COALESCE, 100, events);
        source.start().toCompletableFuture().join();
        source.nativeMouseMoved(event(1, 2));
        source.nativeMouseMoved(event(3, 4));
        source.pause().toCompletableFuture().join();
        Thread.sleep(150);
        source.stop().toCompletableFuture().join();
        assertEquals(1, events.size());
    }

    private MouseMotionEventSource source(MotionOutputStrategy strategy, long interval, Queue<KuudraEvent> events) {
        NativeHookController controller = new NativeHookController() {
            @Override public void acquire() { }
            @Override public void release() { }
        };
        MouseMotionEventSource source = new MouseMotionEventSource(controller,
                new MotionOutputOptions(strategy, interval), () -> { }, () -> { });
        source.setEmitter(events::offer);
        return source;
    }

    private NativeMouseEvent event(int x, int y) {
        return new NativeMouseEvent(NativeMouseEvent.NATIVE_MOUSE_MOVED, 0, x, y, NativeMouseEvent.NOBUTTON);
    }

    private ScreenPosition position(KuudraEvent event) {
        return event.data().get(InteractionEvents.DATA_NAMESPACE, InteractionEvents.POSITION, ScreenPosition.class);
    }
}
