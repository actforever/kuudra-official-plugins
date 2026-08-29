package io.github.actforever.kuudra.windowshost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class NativePipeClient implements AutoCloseable {
    static final int MAX_FRAME_BYTES = 64 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WinNT.HANDLE commandPipe;
    private final WinNT.HANDLE eventPipe;
    private final ConcurrentMap<UUID, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final ExecutorService commands;
    private final Thread eventReader;
    private volatile Consumer<JsonNode> eventConsumer = ignored -> { };

    NativePipeClient(WinNT.HANDLE commandPipe, WinNT.HANDLE eventPipe) {
        this.commandPipe = commandPipe;
        this.eventPipe = eventPipe;
        commands = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "kuudra-windows-native-host-command");
            thread.setDaemon(true);
            return thread;
        });
        eventReader = new Thread(this::readEvents, "kuudra-windows-native-host-events");
        eventReader.setDaemon(true);
        eventReader.start();
    }

    void onEvent(Consumer<JsonNode> consumer) { eventConsumer = consumer; }

    CompletionStage<JsonNode> request(String operation, Object payload) {
        if (!open.get()) return CompletableFuture.failedFuture(new NativeHostException("HOST_DISCONNECTED", "Native host pipe is closed"));
        UUID requestId = UUID.randomUUID();
        CompletableFuture<JsonNode> result = new CompletableFuture<>();
        pending.put(requestId, result);
        try {
            commands.execute(() -> executeRequest(requestId, operation, payload, result));
        } catch (RejectedExecutionException error) {
            pending.remove(requestId);
            result.completeExceptionally(error);
        }
        return result;
    }

    private void executeRequest(UUID requestId, String operation, Object payload, CompletableFuture<JsonNode> result) {
        try {
            ObjectNode envelope = JSON.createObjectNode();
            envelope.put("kind", "request");
            envelope.put("version", 1);
            envelope.put("requestId", requestId.toString());
            envelope.put("operation", operation);
            envelope.set("payload", JSON.valueToTree(payload));
            writeFrame(commandPipe, JSON.writeValueAsBytes(envelope));
            JsonNode response = JSON.readTree(readFrame(commandPipe));
            if (!"response".equals(response.path("kind").asText())
                    || !requestId.toString().equals(response.path("requestId").asText())) {
                throw new NativeHostException("INVALID_RESPONSE", "Native host response does not match the request");
            }
            pending.remove(requestId);
            if (response.path("success").asBoolean(false)) {
                result.completeAsync(() -> response.path("payload"));
            } else {
                NativeHostException failure = new NativeHostException(response.path("errorCode").asText("HOST_ERROR"),
                        response.path("errorMessage").asText("Native host request failed"));
                CompletableFuture.runAsync(() -> result.completeExceptionally(failure));
            }
        } catch (Throwable error) {
            pending.remove(requestId);
            NativeHostException failure = new NativeHostException("HOST_REQUEST_FAILED", "Native host request failed", error);
            CompletableFuture.runAsync(() -> result.completeExceptionally(failure));
        }
    }

    private void readEvents() {
        try {
            while (open.get()) {
                JsonNode envelope = JSON.readTree(readFrame(eventPipe));
                if (!"event".equals(envelope.path("kind").asText())) {
                    throw new NativeHostException("INVALID_EVENT", "Native host event pipe received a non-event frame");
                }
                eventConsumer.accept(envelope);
            }
        } catch (Throwable error) {
            if (open.getAndSet(false)) failPending(new NativeHostException("HOST_DISCONNECTED", "Native host event pipe disconnected", error));
        }
    }

    private byte[] readFrame(WinNT.HANDLE pipe) {
        byte[] header = readExact(pipe, 4);
        int size = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getInt();
        if (size < 1 || size > MAX_FRAME_BYTES) throw new NativeHostException("INVALID_FRAME", "Invalid native host frame size: " + size);
        return readExact(pipe, size);
    }

    private byte[] readExact(WinNT.HANDLE pipe, int size) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(size);
        while (output.size() < size) {
            int requested = size - output.size();
            byte[] buffer = new byte[requested];
            IntByReference read = new IntByReference();
            if (!Kernel32.INSTANCE.ReadFile(pipe, buffer, requested, read, null)) {
                throw new NativeHostException("PIPE_READ_FAILED", "Named Pipe read failed with Windows error " + Kernel32.INSTANCE.GetLastError());
            }
            int count = read.getValue();
            if (count == 0) throw new NativeHostException("HOST_DISCONNECTED", "Native host closed the Named Pipe");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void writeFrame(WinNT.HANDLE pipe, byte[] payload) {
        if (payload.length > MAX_FRAME_BYTES) throw new NativeHostException("FRAME_TOO_LARGE", "Native host frame exceeds " + MAX_FRAME_BYTES + " bytes");
        byte[] frame = ByteBuffer.allocate(payload.length + 4).order(ByteOrder.BIG_ENDIAN).putInt(payload.length).put(payload).array();
        IntByReference written = new IntByReference();
        if (!Kernel32.INSTANCE.WriteFile(pipe, frame, frame.length, written, null) || written.getValue() != frame.length) {
            throw new NativeHostException("PIPE_WRITE_FAILED", "Named Pipe write failed with Windows error " + Kernel32.INSTANCE.GetLastError());
        }
    }

    private void failPending(Throwable error) {
        pending.values().forEach(future -> future.completeExceptionally(error));
        pending.clear();
    }

    @Override public void close() {
        if (!open.getAndSet(false)) return;
        Kernel32.INSTANCE.CloseHandle(commandPipe);
        Kernel32.INSTANCE.CloseHandle(eventPipe);
        commands.shutdownNow();
        failPending(new NativeHostException("HOST_CLOSED", "Native host client closed"));
        eventReader.interrupt();
    }
}
