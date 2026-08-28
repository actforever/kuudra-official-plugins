package io.github.actforever.kuudra.awtrobot;

import java.util.concurrent.*;

/** One physical input queue per plugin ClassLoader, shared by every configured Handler resource. */
final class SharedRobotDevice implements RobotDevice {
    static final SharedRobotDevice INSTANCE = new SharedRobotDevice(AwtRobotDriver::new);
    private final Object monitor = new Object();
    private final java.util.function.Supplier<RobotDriver> driverFactory;
    private ExecutorService executor;
    private RobotDriver driver;

    SharedRobotDevice(java.util.function.Supplier<RobotDriver> driverFactory) {
        this.driverFactory = java.util.Objects.requireNonNull(driverFactory);
    }

    @Override public CompletionStage<Void> submit(ThrowingTask task) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        executor().execute(() -> {
            try { task.run(); result.complete(null); }
            catch (Throwable error) { result.completeExceptionally(error); }
        });
        return result;
    }

    @Override public RobotDriver driver() {
        synchronized (monitor) { if (driver == null) driver = driverFactory.get(); return driver; }
    }

    private ExecutorService executor() {
        synchronized (monitor) {
            if (executor == null || executor.isShutdown()) executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "kuudra-awt-robot"); thread.setDaemon(true); return thread;
            });
            return executor;
        }
    }

    @Override public void close() {
        ExecutorService current;
        synchronized (monitor) { current = executor; executor = null; driver = null; }
        if (current != null) current.shutdownNow();
    }
}
