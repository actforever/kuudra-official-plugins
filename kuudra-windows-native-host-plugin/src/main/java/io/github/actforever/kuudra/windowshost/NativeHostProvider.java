package io.github.actforever.kuudra.windowshost;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.*;
import io.github.actforever.kuudra.plugin.PluginLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

final class NativeHostProvider implements WindowsNativeHost.Provider {
    private static final String RESOURCE = "/META-INF/native/win-x64/Kuudra.Windows.PrivilegedHost.exe";
    private static final int GENERIC_READ = 0x80000000;
    private static final int GENERIC_WRITE = 0x40000000;
    private static final int OPEN_EXISTING = 3;
    private static final long LAUNCH_TIMEOUT_MILLIS = 120_000;

    interface PipeKernel32 extends com.sun.jna.win32.StdCallLibrary {
        PipeKernel32 INSTANCE = Native.load("kernel32", PipeKernel32.class);
        boolean GetNamedPipeServerProcessId(WinNT.HANDLE pipe, IntByReference serverProcessId);
    }

    private final Path home;
    private final PluginLogger logger;
    private final Set<Lease> leases = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CompletableFuture<ProcessOperationResult>> operations = new ConcurrentHashMap<>();
    private NativePipeClient client;
    private WinNT.HANDLE processHandle;
    private long serverPid;
    private boolean closed;

    NativeHostProvider(Path home, PluginLogger logger) {
        this.home = home;
        this.logger = logger;
    }

    @Override public synchronized ProcessControlLease acquire(String owner, boolean allowElevation,
                                                               Collection<ProcessTarget> targets, long maxDurationMillis) {
        if (closed) throw new NativeHostException("HOST_CLOSED", "Windows native host is closed");
        if (!allowElevation) throw new NativeHostException("ELEVATION_NOT_ALLOWED", "Process control requires options.allowElevation: true");
        if (targets == null || targets.isEmpty()) throw new IllegalArgumentException("At least one process target is required");
        ensureWindows();
        ensureClient();
        String ownerId = owner + ":" + UUID.randomUUID();
        Map<String, String> authorized = new LinkedHashMap<>();
        for (ProcessTarget target : targets) {
            if (authorized.putIfAbsent(target.alias(), target.executablePath().toString()) != null) {
                throw new IllegalArgumentException("Duplicate target alias: " + target.alias());
            }
        }
        join(client.request("ACQUIRE_PROCESS_CONTROL", Map.of("owner", ownerId, "targets", authorized,
                "maxDurationMillis", maxDurationMillis)));
        Lease lease = new Lease(ownerId, maxDurationMillis);
        leases.add(lease);
        return lease;
    }

    private void ensureWindows() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")
                || !System.getProperty("os.arch", "").contains("64")) {
            throw new NativeHostException("UNSUPPORTED_PLATFORM", "Windows native host requires 64-bit Windows");
        }
    }

    private void ensureClient() {
        if (client != null) return;
        try {
            Path executable = extractHost();
            String commandPipeName = "kuudra-command-" + UUID.randomUUID();
            String eventPipeName = "kuudra-event-" + UUID.randomUUID();
            Path journal = home.resolve("state").resolve("active-process-operations.json");
            Files.createDirectories(journal.getParent());
            long jvmPid = ProcessHandle.current().pid();
            ShellAPI.SHELLEXECUTEINFO launch = new ShellAPI.SHELLEXECUTEINFO();
            launch.fMask = 0x00000040;
            launch.lpVerb = "runas";
            launch.lpFile = executable.toString();
            launch.lpParameters = "--command-pipe \"" + commandPipeName + "\" --event-pipe \"" + eventPipeName
                    + "\" --client-pid " + jvmPid + " --journal \"" + journal + "\"";
            launch.nShow = WinUser.SW_HIDE;
            if (!Shell32.INSTANCE.ShellExecuteEx(launch)) {
                int error = Kernel32.INSTANCE.GetLastError();
                throw new NativeHostException(error == 1223 ? "UAC_CANCELLED" : "HOST_LAUNCH_FAILED",
                        "Unable to launch privileged native host; Windows error " + error);
            }
            processHandle = launch.hProcess;
            serverPid = Kernel32.INSTANCE.GetProcessId(processHandle);
            WinNT.HANDLE commandPipe = connectPipe(commandPipeName, serverPid);
            WinNT.HANDLE eventPipe = connectPipe(eventPipeName, serverPid);
            NativePipeClient connected = new NativePipeClient(commandPipe, eventPipe);
            connected.onEvent(this::handleEvent);
            JsonNode hello = join(connected.request("HELLO", Map.of("clientPid", jvmPid, "protocolMajor", 1, "protocolMinor", 0)));
            if (hello.path("serverPid").asLong() != serverPid || hello.path("protocolMajor").asInt() != 1) {
                connected.close();
                throw new NativeHostException("HANDSHAKE_FAILED", "Native host identity or protocol mismatch");
            }
            client = connected;
            logger.info("windows-native-host.active", Map.of("serverPid", serverPid));
        } catch (NativeHostException error) {
            throw error;
        } catch (Exception error) {
            throw new NativeHostException("HOST_START_FAILED", "Failed to start Windows native host", error);
        }
    }

    private WinNT.HANDLE connectPipe(String pipeName, long expectedServerPid) throws InterruptedException {
        String path = "\\\\.\\pipe\\" + pipeName;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LAUNCH_TIMEOUT_MILLIS);
        while (System.nanoTime() < deadline) {
            if (Kernel32.INSTANCE.WaitNamedPipe(path, 1_000)) {
                WinNT.HANDLE pipe = Kernel32.INSTANCE.CreateFile(path, GENERIC_READ | GENERIC_WRITE, 0, null,
                        OPEN_EXISTING, 0, null);
                if (!WinBase.INVALID_HANDLE_VALUE.equals(pipe)) {
                    IntByReference pid = new IntByReference();
                    if (!PipeKernel32.INSTANCE.GetNamedPipeServerProcessId(pipe, pid)
                            || Integer.toUnsignedLong(pid.getValue()) != expectedServerPid) {
                        Kernel32.INSTANCE.CloseHandle(pipe);
                        throw new NativeHostException("SERVER_IDENTITY_MISMATCH", "Named Pipe server PID does not match launched host");
                    }
                    return pipe;
                }
            }
            Thread.sleep(50);
        }
        throw new NativeHostException("HOST_START_TIMEOUT", "Timed out waiting for privileged native host");
    }

    private Path extractHost() throws Exception {
        Path directory = home.resolve("native").resolve("0.2.0-alpha-1").resolve("win-x64");
        Files.createDirectories(directory);
        Path executable = directory.resolve("Kuudra.Windows.PrivilegedHost.exe");
        byte[] expectedBytes;
        try (InputStream input = NativeHostProvider.class.getResourceAsStream(RESOURCE + ".sha256")) {
            if (input == null) throw new NativeHostException("HOST_RESOURCE_MISSING", "Embedded native host checksum is missing");
            expectedBytes = input.readAllBytes();
        }
        String expected = new String(expectedBytes, StandardCharsets.US_ASCII).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (Files.isRegularFile(executable) && sha256(executable).equals(expected)) return executable;
        Path temporary = Files.createTempFile(directory, "native-host-", ".exe");
        try (InputStream input = NativeHostProvider.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new NativeHostException("HOST_RESOURCE_MISSING", "Embedded native host executable is missing");
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!sha256(temporary).equals(expected)) {
            Files.deleteIfExists(temporary);
            throw new NativeHostException("HOST_HASH_MISMATCH", "Embedded native host checksum mismatch");
        }
        Files.move(temporary, executable, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return executable;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void handleEvent(JsonNode envelope) {
        if (!envelope.path("operation").asText().equals("PROCESS_OPERATION_COMPLETED")) return;
        JsonNode payload = envelope.path("payload");
        UUID id = UUID.fromString(payload.path("operationId").asText());
        CompletableFuture<ProcessOperationResult> completion = operations.remove(id);
        if (completion != null) {
            ProcessOperationResult result = new ProcessOperationResult(id, payload.path("target").asText(),
                    payload.path("pid").asLong(), ProcessOperationOutcome.valueOf(payload.path("outcome").asText()),
                    Instant.parse(payload.path("startedAt").asText()), Instant.parse(payload.path("completedAt").asText()));
            completion.completeAsync(() -> result);
        }
    }

    private static JsonNode join(CompletionStage<JsonNode> stage) {
        try { return stage.toCompletableFuture().get(30, TimeUnit.SECONDS); }
        catch (ExecutionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new NativeHostException("HOST_REQUEST_FAILED", "Native host request failed", error.getCause());
        } catch (Exception error) { throw new NativeHostException("HOST_REQUEST_FAILED", "Native host request failed", error); }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        for (Lease lease : List.copyOf(leases)) lease.close();
        if (client != null) {
            try { join(client.request("SHUTDOWN", Map.of())); }
            catch (RuntimeException error) { logger.error("windows-native-host.shutdown-failed", error); }
            client.close();
            client = null;
        }
        if (processHandle != null) {
            Kernel32.INSTANCE.CloseHandle(processHandle);
            processHandle = null;
        }
    }

    private final class Lease implements ProcessControlLease {
        private final String owner;
        private final long maxDurationMillis;
        private boolean leaseClosed;

        private Lease(String owner, long maxDurationMillis) { this.owner = owner; this.maxDurationMillis = maxDurationMillis; }

        @Override public CompletionStage<ProcessOperation> suspend(String target, Long pid, Duration duration) {
            if (leaseClosed) return CompletableFuture.failedFuture(new NativeHostException("LEASE_CLOSED", "Process control lease is closed"));
            long millis = duration.toMillis();
            if (millis < 1 || millis > maxDurationMillis) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("Duration must be between 1 and " + maxDurationMillis + " ms"));
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("owner", owner); payload.put("target", target); payload.put("durationMillis", millis);
            if (pid != null) payload.put("pid", pid);
            return client.request("SUSPEND", payload).thenApply(response -> {
                UUID id = UUID.fromString(response.path("operationId").asText());
                CompletableFuture<ProcessOperationResult> completion = new CompletableFuture<>();
                operations.put(id, completion);
                return new ProcessOperation(id, response.path("pid").asLong(), Instant.parse(response.path("deadline").asText()), completion);
            });
        }

        @Override public CompletionStage<Void> resume(String target, Long pid) {
            Map<String, Object> payload = new LinkedHashMap<>(); payload.put("owner", owner); payload.put("target", target);
            if (pid != null) payload.put("pid", pid);
            return client.request("RESUME", payload).thenApply(ignored -> null);
        }

        @Override public CompletionStage<Void> restoreAll() {
            if (client == null) return CompletableFuture.completedFuture(null);
            return client.request("RESTORE_OWNER", Map.of("owner", owner)).thenApply(ignored -> null);
        }

        @Override public void close() {
            if (leaseClosed) return;
            leaseClosed = true;
            try { restoreAll().toCompletableFuture().get(10, TimeUnit.SECONDS); }
            catch (Exception error) { logger.error("windows-native-host.owner-restore-failed", error); }
            leases.remove(this);
        }
    }
}
