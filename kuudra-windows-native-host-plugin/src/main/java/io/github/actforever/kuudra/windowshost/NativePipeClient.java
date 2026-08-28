package io.github.actforever.kuudra.windowshost;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class NativePipeClient implements AutoCloseable {
    static final int MAX_FRAME_BYTES = 64 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WinNT.HANDLE pipe;
    private final Map<UUID, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final Object writeLock = new Object();
    private final Thread reader;
    private volatile Consumer<JsonNode> eventConsumer = ignored -> { };

    NativePipeClient(WinNT.HANDLE pipe) {
        this.pipe = pipe;
        reader = new Thread(this::readLoop, "kuudra-windows-native-host-pipe");
        reader.setDaemon(true);
        reader.start();
    }

    void onEvent(Consumer<JsonNode> consumer) { eventConsumer = consumer; }

    CompletionStage<JsonNode> request(String operation, Object payload) {
        if (!open.get()) return CompletableFuture.failedFuture(new NativeHostException("HOST_DISCONNECTED", "Native host pipe is closed"));
        UUID requestId = UUID.randomUUID();
        CompletableFuture<JsonNode> result = new CompletableFuture<>();
        pending.put(requestId, result);
        ObjectNode envelope = JSON.createObjectNode();
        envelope.put("kind", "request");
        envelope.put("version", 1);
        envelope.put("requestId", requestId.toString());
        envelope.put("operation", operation);
        envelope.set("payload", JSON.valueToTree(payload));
        try {
            writeFrame(JSON.writeValueAsBytes(envelope));
        } catch (RuntimeException | IOException error) {
            pending.remove(requestId);
            result.completeExceptionally(error);
        }
        return result;
    }

    private void readLoop() {
        try {
            while (open.get()) dispatch(JSON.readTree(readFrame()));
        } catch (Throwable error) {
            if (open.getAndSet(false)) failPending(new NativeHostException("HOST_DISCONNECTED", "Native host pipe disconnected", error));
        }
    }

    private void dispatch(JsonNode envelope) {
        String kind = envelope.path("kind").asText();
        if (kind.equals("event")) {
            eventConsumer.accept(envelope);
            return;
        }
        UUID requestId = UUID.fromString(envelope.path("requestId").asText());
        CompletableFuture<JsonNode> result = pending.remove(requestId);
        if (result == null) return;
        if (envelope.path("success").asBoolean(false)) result.complete(envelope.path("payload"));
        else result.completeExceptionally(new NativeHostException(envelope.path("errorCode").asText("HOST_ERROR"),
                envelope.path("errorMessage").asText("Native host request failed")));
    }

    private byte[] readFrame() {
        byte[] header = readExact(4);
        int size = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getInt();
        if (size < 1 || size > MAX_FRAME_BYTES) throw new NativeHostException("INVALID_FRAME", "Invalid native host frame size: " + size);
        return readExact(size);
    }

    private byte[] readExact(int size) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(size);
        while (output.size() < size) {
            int remaining = size - output.size();
            byte[] buffer = new byte[remaining];
            IntByReference read = new IntByReference();
            if (!Kernel32.INSTANCE.ReadFile(pipe, buffer, remaining, read, null)) {
                throw new NativeHostException("PIPE_READ_FAILED", "Named Pipe read failed with Windows error " + Kernel32.INSTANCE.GetLastError());
            }
            int count = read.getValue();
            if (count == 0) throw new NativeHostException("HOST_DISCONNECTED", "Native host closed the Named Pipe");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private void writeFrame(byte[] payload) {
        if (payload.length > MAX_FRAME_BYTES) throw new NativeHostException("FRAME_TOO_LARGE", "Native host frame exceeds " + MAX_FRAME_BYTES + " bytes");
        byte[] frame = ByteBuffer.allocate(payload.length + 4).order(ByteOrder.BIG_ENDIAN).putInt(payload.length).put(payload).array();
        synchronized (writeLock) {
            IntByReference written = new IntByReference();
            if (!Kernel32.INSTANCE.WriteFile(pipe, frame, frame.length, written, null) || written.getValue() != frame.length) {
                throw new NativeHostException("PIPE_WRITE_FAILED", "Named Pipe write failed with Windows error " + Kernel32.INSTANCE.GetLastError());
            }
        }
    }

    private void failPending(Throwable error) {
        pending.values().forEach(future -> future.completeExceptionally(error));
        pending.clear();
    }

    @Override public void close() {
        if (!open.getAndSet(false)) return;
        Kernel32.INSTANCE.CloseHandle(pipe);
        failPending(new NativeHostException("HOST_CLOSED", "Native host client closed"));
        reader.interrupt();
    }
}
