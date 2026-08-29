using Microsoft.Win32.SafeHandles;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text;
using System.Text.Json;

namespace Kuudra.Windows.PrivilegedHost;

internal static class Program
{
    private const int PipeBufferBytes = 64 * 1024;

    public static async Task<int> Main(string[] args)
    {
        try
        {
            var options = Arguments.Parse(args);
            var service = new ProcessControlService(options.Journal);
            await service.RecoverAsync();
            await using var commandServer = CreatePipe(options.CommandPipe);
            await using var eventServer = CreatePipe(options.EventPipe);
            await commandServer.WaitForConnectionAsync();
            await eventServer.WaitForConnectionAsync();
            VerifyClient(commandServer, options.ClientPid, "command");
            VerifyClient(eventServer, options.ClientPid, "event");

            var connection = new ProtocolConnection(commandServer, eventServer, service, options.ClientPid);
            service.Attach(connection);
            try { await connection.RunAsync(); }
            catch (IOException) { }
            finally { await service.WaitUntilIdleAsync(); }
            return 0;
        }
        catch (Exception error)
        {
            await Console.Error.WriteLineAsync(error.ToString());
            return 1;
        }
    }

    private static void VerifyClient(NamedPipeServerStream server, uint expectedClientPid, string role)
    {
        if (!NativeMethods.GetNamedPipeClientProcessId(server.SafePipeHandle, out var clientPid)
            || clientPid != expectedClientPid)
            throw new HostFailure("CLIENT_IDENTITY_MISMATCH", $"Named Pipe {role} client PID does not match the launching JVM");
    }

    private static NamedPipeServerStream CreatePipe(string name)
    {
        var identity = WindowsIdentity.GetCurrent();
        var sid = identity.User ?? throw new InvalidOperationException("Cannot resolve the current Windows user SID");
        var security = new PipeSecurity();
        security.SetOwner(sid);
        security.SetAccessRuleProtection(true, false);
        security.AddAccessRule(new PipeAccessRule(sid, PipeAccessRights.FullControl, AccessControlType.Allow));
        return NamedPipeServerStreamAcl.Create(name, PipeDirection.InOut, 1, PipeTransmissionMode.Byte,
            PipeOptions.Asynchronous, PipeBufferBytes, PipeBufferBytes, security);
    }
}

internal sealed record Arguments(string CommandPipe, string EventPipe, uint ClientPid, string Journal)
{
    public static Arguments Parse(string[] args)
    {
        var values = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        for (var i = 0; i < args.Length; i += 2)
        {
            if (i + 1 >= args.Length || !args[i].StartsWith("--", StringComparison.Ordinal))
                throw new ArgumentException("Expected --name value arguments");
            values[args[i][2..]] = args[i + 1];
        }
        return new Arguments(Required(values, "command-pipe"), Required(values, "event-pipe"),
            uint.Parse(Required(values, "client-pid")), Required(values, "journal"));
    }

    private static string Required(Dictionary<string, string> values, string key) =>
        values.TryGetValue(key, out var value) && !string.IsNullOrWhiteSpace(value)
            ? value : throw new ArgumentException($"Missing --{key}");
}

internal sealed class ProtocolConnection
{
    private const int MaxFrameBytes = 64 * 1024;
    private readonly Stream commandStream;
    private readonly Stream eventStream;
    private readonly ProcessControlService service;
    private readonly uint expectedClientPid;
    private readonly SemaphoreSlim commandWriteLock = new(1, 1);
    private readonly SemaphoreSlim eventWriteLock = new(1, 1);

    public ProtocolConnection(Stream commandStream, Stream eventStream, ProcessControlService service, uint expectedClientPid)
    {
        this.commandStream = commandStream;
        this.eventStream = eventStream;
        this.service = service;
        this.expectedClientPid = expectedClientPid;
    }

    public async Task RunAsync()
    {
        while (true)
        {
            JsonDocument request;
            try { request = await ReadFrameAsync(); }
            catch (EndOfStreamException) { return; }
            using (request)
            {
                var root = request.RootElement;
                var requestId = root.GetProperty("requestId").GetString() ?? string.Empty;
                var operation = root.GetProperty("operation").GetString() ?? string.Empty;
                try
                {
                    var payload = root.GetProperty("payload");
                    object response = operation switch
                    {
                        "HELLO" => Hello(payload),
                        "ACQUIRE_PROCESS_CONTROL" => service.Acquire(payload),
                        "SUSPEND" => await service.SuspendAsync(payload),
                        "RESUME" => await service.ResumeAsync(payload, "EXPLICIT_RESUME"),
                        "RESTORE_OWNER" => await service.RestoreOwnerAsync(payload.GetProperty("owner").GetString()!, "OWNER_RESTORED"),
                        "SHUTDOWN" => await service.ShutdownAsync(),
                        _ => throw new HostFailure("UNKNOWN_OPERATION", $"Unknown native host operation: {operation}")
                    };
                    await WriteCommandAsync(new { kind = "response", version = 1, requestId, success = true, payload = response });
                    if (operation == "SHUTDOWN") return;
                }
                catch (HostFailure error)
                {
                    await WriteCommandAsync(new { kind = "response", version = 1, requestId, success = false,
                        errorCode = error.Code, errorMessage = error.Message });
                }
                catch (Exception error)
                {
                    await WriteCommandAsync(new { kind = "response", version = 1, requestId, success = false,
                        errorCode = "INTERNAL_ERROR", errorMessage = error.Message });
                }
            }
        }
    }

    private object Hello(JsonElement payload)
    {
        if (payload.GetProperty("clientPid").GetUInt32() != expectedClientPid)
            throw new HostFailure("CLIENT_IDENTITY_MISMATCH", "HELLO client PID mismatch");
        if (payload.GetProperty("protocolMajor").GetInt32() != 1)
            throw new HostFailure("PROTOCOL_MISMATCH", "Only native host protocol major version 1 is supported");
        return new { serverPid = Environment.ProcessId, protocolMajor = 1, protocolMinor = 0,
            capabilities = new[] { "PROCESS_CONTROL" } };
    }

    public Task OperationCompletedAsync(OperationState operation) => WriteEventAsync(new
    {
        kind = "event", version = 1, operation = "PROCESS_OPERATION_COMPLETED",
        payload = new { operationId = operation.Id, target = operation.Target, pid = operation.Pid,
            outcome = operation.Outcome, startedAt = operation.StartedAt, completedAt = operation.CompletedAt }
    });

    private async Task<JsonDocument> ReadFrameAsync()
    {
        var header = new byte[4];
        await ReadExactAsync(header);
        var length = System.Buffers.Binary.BinaryPrimitives.ReadInt32BigEndian(header);
        if (length is < 1 or > MaxFrameBytes) throw new HostFailure("INVALID_FRAME", $"Invalid frame size: {length}");
        var payload = new byte[length];
        await ReadExactAsync(payload);
        return JsonDocument.Parse(payload);
    }

    private async Task ReadExactAsync(byte[] buffer)
    {
        var offset = 0;
        while (offset < buffer.Length)
        {
            var read = await commandStream.ReadAsync(buffer.AsMemory(offset));
            if (read == 0) throw new EndOfStreamException();
            offset += read;
        }
    }

    private Task WriteCommandAsync(object value) => WriteAsync(commandStream, commandWriteLock, value);
    private Task WriteEventAsync(object value) => WriteAsync(eventStream, eventWriteLock, value);

    private static async Task WriteAsync(Stream stream, SemaphoreSlim writeLock, object value)
    {
        var payload = JsonSerializer.SerializeToUtf8Bytes(value, JsonOptions.Default);
        if (payload.Length > MaxFrameBytes) throw new HostFailure("FRAME_TOO_LARGE", "Response exceeds the frame limit");
        var header = new byte[4];
        System.Buffers.Binary.BinaryPrimitives.WriteInt32BigEndian(header, payload.Length);
        await writeLock.WaitAsync();
        try
        {
            await stream.WriteAsync(header);
            await stream.WriteAsync(payload);
        }
        finally { writeLock.Release(); }
    }
}

internal sealed class ProcessControlService
{
    private readonly string journal;
    private readonly object sync = new();
    private readonly Dictionary<string, OwnerPolicy> owners = new(StringComparer.Ordinal);
    private readonly Dictionary<Guid, OperationState> operations = new();
    private ProtocolConnection? connection;
    private bool shuttingDown;

    public ProcessControlService(string journal) => this.journal = Path.GetFullPath(journal);
    public void Attach(ProtocolConnection connected) => connection = connected;

    public object Acquire(JsonElement payload)
    {
        var owner = RequiredString(payload, "owner");
        var maxDuration = payload.GetProperty("maxDurationMillis").GetInt64();
        if (maxDuration is < 100 or > 86_400_000)
            throw new HostFailure("INVALID_DURATION_LIMIT", "maxDurationMillis must be between 100 and 86400000");
        var targets = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var target in payload.GetProperty("targets").EnumerateObject())
        {
            var path = Path.GetFullPath(target.Value.GetString() ?? string.Empty);
            if (!Path.IsPathFullyQualified(path)) throw new HostFailure("INVALID_TARGET", $"Target path must be absolute: {path}");
            targets[target.Name] = path;
        }
        if (targets.Count == 0) throw new HostFailure("INVALID_TARGET", "At least one process target is required");
        lock (sync)
        {
            if (shuttingDown) throw new HostFailure("HOST_SHUTTING_DOWN", "Native host is shutting down");
            owners[owner] = new OwnerPolicy(targets, maxDuration);
        }
        return new { owner };
    }

    public async Task<object> SuspendAsync(JsonElement payload)
    {
        var ownerId = RequiredString(payload, "owner");
        var target = RequiredString(payload, "target");
        var duration = payload.GetProperty("durationMillis").GetInt64();
        OwnerPolicy owner;
        lock (sync)
        {
            if (!owners.TryGetValue(ownerId, out owner!)) throw new HostFailure("UNKNOWN_OWNER", "Unknown process-control owner");
            if (duration is < 100 || duration > owner.MaxDurationMillis)
                throw new HostFailure("INVALID_DURATION", $"durationMillis must be between 100 and {owner.MaxDurationMillis}");
        }
        if (!owner.Targets.TryGetValue(target, out var allowedPath)) throw new HostFailure("UNKNOWN_TARGET", $"Unknown target alias: {target}");
        var requestedPid = payload.TryGetProperty("pid", out var pidProperty) ? pidProperty.GetInt64() : (long?)null;
        using var process = ResolveProcess(allowedPath, requestedPid);
        var identity = new ProcessIdentity(process.Id, process.StartTime.ToUniversalTime(), QueryImagePath(process));
        lock (sync)
        {
            if (operations.Values.Any(value => value.Pid == identity.Pid && value.ProcessStartedAt == identity.StartedAt))
                throw new HostFailure("TARGET_ALREADY_SUSPENDED", "The target process already has an active suspension lease");
        }

        var suspendedThreads = SuspendThreads(process);
        var operation = new OperationState(Guid.NewGuid(), ownerId, target, identity.Pid, identity.StartedAt,
            identity.ImagePath, suspendedThreads, DateTimeOffset.UtcNow, DateTimeOffset.UtcNow.AddMilliseconds(duration));
        lock (sync) { operations.Add(operation.Id, operation); SaveJournalLocked(); }
        _ = CompleteAtDeadlineAsync(operation.Id, duration);
        await Task.Yield();
        return new { operationId = operation.Id, pid = operation.Pid, deadline = operation.Deadline };
    }

    public async Task<object> ResumeAsync(JsonElement payload, string outcome)
    {
        var owner = RequiredString(payload, "owner");
        var target = RequiredString(payload, "target");
        var pid = payload.TryGetProperty("pid", out var pidProperty) ? pidProperty.GetInt64() : (long?)null;
        List<Guid> matches;
        lock (sync)
        {
            matches = operations.Values.Where(value => value.Owner == owner && value.Target == target
                    && (pid == null || value.Pid == pid)).Select(value => value.Id).ToList();
        }
        if (matches.Count > 1 && pid == null) throw new HostFailure("AMBIGUOUS_TARGET", "Multiple active target processes require pid");
        foreach (var id in matches) await CompleteAsync(id, outcome);
        return new { restored = matches.Count };
    }

    public async Task<object> RestoreOwnerAsync(string owner, string outcome)
    {
        List<Guid> ids;
        lock (sync) { ids = operations.Values.Where(value => value.Owner == owner).Select(value => value.Id).ToList(); }
        foreach (var id in ids) await CompleteAsync(id, outcome);
        lock (sync) { owners.Remove(owner); }
        return new { restored = ids.Count };
    }

    public async Task<object> ShutdownAsync()
    {
        List<Guid> ids;
        lock (sync) { shuttingDown = true; ids = operations.Keys.ToList(); }
        foreach (var id in ids) await CompleteAsync(id, "HOST_SHUTDOWN");
        return new { restored = ids.Count };
    }

    private async Task CompleteAtDeadlineAsync(Guid id, long durationMillis)
    {
        await Task.Delay(TimeSpan.FromMilliseconds(durationMillis));
        await CompleteAsync(id, "EXPIRED");
    }

    private async Task CompleteAsync(Guid id, string outcome)
    {
        OperationState? operation;
        lock (sync)
        {
            if (!operations.Remove(id, out operation)) return;
        }
        var targetExited = !ResumeThreads(operation);
        operation.Outcome = targetExited ? "TARGET_EXITED" : outcome;
        operation.CompletedAt = DateTimeOffset.UtcNow;
        lock (sync) { SaveJournalLocked(); }
        if (connection != null)
        {
            try { await connection.OperationCompletedAsync(operation); }
            catch (IOException) { }
        }
    }

    public async Task RecoverAsync()
    {
        if (!File.Exists(journal)) return;
        try
        {
            var recovered = JsonSerializer.Deserialize<List<JournalOperation>>(await File.ReadAllTextAsync(journal), JsonOptions.Default) ?? [];
            foreach (var operation in recovered)
            {
                try
                {
                    using var process = Process.GetProcessById(checked((int)operation.Pid));
                    if (process.StartTime.ToUniversalTime() == operation.ProcessStartedAt.UtcDateTime
                        && PathsEqual(QueryImagePath(process), operation.ImagePath))
                        ResumeThreadIds(operation.ThreadIds);
                }
                catch { }
            }
            File.Delete(journal);
        }
        catch (Exception error) { throw new HostFailure("RECOVERY_FAILED", $"Cannot recover active process operations: {error.Message}"); }
    }

    public async Task WaitUntilIdleAsync()
    {
        while (true)
        {
            lock (sync) { if (operations.Count == 0) return; }
            await Task.Delay(100);
        }
    }

    private void SaveJournalLocked()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(journal)!);
        if (operations.Count == 0)
        {
            if (File.Exists(journal)) File.Delete(journal);
            return;
        }
        var temporary = journal + ".tmp";
        var records = operations.Values.Select(value => new JournalOperation(value.Pid, value.ProcessStartedAt,
            value.ImagePath, value.ThreadIds, value.Deadline)).ToList();
        File.WriteAllText(temporary, JsonSerializer.Serialize(records, JsonOptions.Default));
        File.Move(temporary, journal, true);
    }

    private static Process ResolveProcess(string allowedPath, long? requestedPid)
    {
        if (requestedPid != null)
        {
            Process process;
            try { process = Process.GetProcessById(checked((int)requestedPid.Value)); }
            catch (Exception error) { throw new HostFailure("TARGET_NOT_FOUND", $"Target PID {requestedPid} is not running: {error.Message}"); }
            if (!PathsEqual(QueryImagePath(process), allowedPath)) { process.Dispose(); throw new HostFailure("TARGET_PATH_MISMATCH", "PID image path does not match the authorized target"); }
            return process;
        }
        var matches = new List<Process>();
        foreach (var process in Process.GetProcesses())
        {
            try
            {
                if (PathsEqual(QueryImagePath(process), allowedPath)) matches.Add(process);
                else process.Dispose();
            }
            catch { process.Dispose(); }
        }
        if (matches.Count == 0) throw new HostFailure("TARGET_NOT_FOUND", $"No running process matches {allowedPath}");
        if (matches.Count > 1)
        {
            matches.ForEach(process => process.Dispose());
            throw new HostFailure("AMBIGUOUS_TARGET", "Multiple matching processes require pid");
        }
        return matches[0];
    }

    private static string QueryImagePath(Process process) => Path.GetFullPath(process.MainModule?.FileName
        ?? throw new HostFailure("TARGET_QUERY_FAILED", $"Cannot query image path for PID {process.Id}"));
    private static bool PathsEqual(string left, string right) => string.Equals(Path.GetFullPath(left).TrimEnd('\\'),
        Path.GetFullPath(right).TrimEnd('\\'), StringComparison.OrdinalIgnoreCase);

    private static List<int> SuspendThreads(Process process)
    {
        var suspended = new List<int>();
        try
        {
            foreach (ProcessThread thread in process.Threads)
            {
                using var handle = NativeMethods.OpenThread(NativeMethods.ThreadSuspendResume, false, (uint)thread.Id);
                if (handle.IsInvalid || NativeMethods.SuspendThread(handle) == uint.MaxValue)
                    throw new HostFailure("THREAD_SUSPEND_FAILED", $"Cannot suspend thread {thread.Id}; Windows error {Marshal.GetLastWin32Error()}");
                suspended.Add(thread.Id);
            }
            if (suspended.Count == 0) throw new HostFailure("TARGET_EXITED", "Target process has no suspendable threads");
            return suspended;
        }
        catch
        {
            ResumeThreadIds(suspended);
            throw;
        }
    }

    private static bool ResumeThreads(OperationState operation)
    {
        try
        {
            using var process = Process.GetProcessById(checked((int)operation.Pid));
            if (process.StartTime.ToUniversalTime() != operation.ProcessStartedAt.UtcDateTime
                || !PathsEqual(QueryImagePath(process), operation.ImagePath)) return false;
        }
        catch { return false; }
        ResumeThreadIds(operation.ThreadIds);
        return true;
    }

    private static void ResumeThreadIds(IEnumerable<int> threadIds)
    {
        foreach (var threadId in threadIds)
        {
            using var handle = NativeMethods.OpenThread(NativeMethods.ThreadSuspendResume, false, (uint)threadId);
            if (!handle.IsInvalid) NativeMethods.ResumeThread(handle);
        }
    }

    private static string RequiredString(JsonElement payload, string name) =>
        payload.TryGetProperty(name, out var value) && !string.IsNullOrWhiteSpace(value.GetString())
            ? value.GetString()! : throw new HostFailure("INVALID_REQUEST", $"Missing {name}");
}

internal sealed record OwnerPolicy(Dictionary<string, string> Targets, long MaxDurationMillis);
internal sealed record ProcessIdentity(long Pid, DateTime StartedAt, string ImagePath);
internal sealed record JournalOperation(long Pid, DateTimeOffset ProcessStartedAt, string ImagePath,
    List<int> ThreadIds, DateTimeOffset Deadline);

internal sealed class OperationState
{
    public OperationState(Guid id, string owner, string target, long pid, DateTime processStartedAt, string imagePath,
        List<int> threadIds, DateTimeOffset startedAt, DateTimeOffset deadline)
    {
        Id = id; Owner = owner; Target = target; Pid = pid; ProcessStartedAt = new DateTimeOffset(processStartedAt, TimeSpan.Zero);
        ImagePath = imagePath; ThreadIds = threadIds; StartedAt = startedAt; Deadline = deadline;
    }
    public Guid Id { get; }
    public string Owner { get; }
    public string Target { get; }
    public long Pid { get; }
    public DateTimeOffset ProcessStartedAt { get; }
    public string ImagePath { get; }
    public List<int> ThreadIds { get; }
    public DateTimeOffset StartedAt { get; }
    public DateTimeOffset Deadline { get; }
    public string Outcome { get; set; } = "EXPIRED";
    public DateTimeOffset CompletedAt { get; set; }
}

internal sealed class HostFailure(string code, string message) : Exception(message) { public string Code { get; } = code; }

internal static class JsonOptions
{
    public static readonly JsonSerializerOptions Default = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        WriteIndented = false
    };
}

internal static partial class NativeMethods
{
    public const uint ThreadSuspendResume = 0x0002;

    [LibraryImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static partial bool GetNamedPipeClientProcessId(SafePipeHandle pipe, out uint clientProcessId);

    [LibraryImport("kernel32.dll", SetLastError = true)]
    public static partial SafeFileHandle OpenThread(uint desiredAccess, [MarshalAs(UnmanagedType.Bool)] bool inheritHandle, uint threadId);

    [LibraryImport("kernel32.dll", SetLastError = true)]
    public static partial uint SuspendThread(SafeFileHandle thread);

    [LibraryImport("kernel32.dll", SetLastError = true)]
    public static partial uint ResumeThread(SafeFileHandle thread);
}
