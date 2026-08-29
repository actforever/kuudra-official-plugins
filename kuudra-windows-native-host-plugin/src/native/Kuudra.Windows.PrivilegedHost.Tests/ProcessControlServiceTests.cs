using System.Diagnostics;
using System.Text.Json;
using Xunit;

namespace Kuudra.Windows.PrivilegedHost.Tests;

public sealed class ProcessControlServiceTests
{
    [Fact]
    public void ArgumentsRequireEveryBootstrapValue()
    {
        var parsed = Arguments.Parse(["--command-pipe", "command", "--event-pipe", "event",
            "--client-pid", "42", "--journal", "state.json"]);
        Assert.Equal("command", parsed.CommandPipe);
        Assert.Equal("event", parsed.EventPipe);
        Assert.Equal((uint)42, parsed.ClientPid);
        Assert.Throws<ArgumentException>(() => Arguments.Parse(["--command-pipe", "missing"]));
    }

    [Fact]
    public void AcquireRejectsUnsafeDurationCeiling()
    {
        var service = new ProcessControlService(Path.Combine(Path.GetTempPath(), $"kuudra-{Guid.NewGuid()}.json"));
        using var payload = JsonDocument.Parse("""{"owner":"test","targets":{"cmd":"C:\\Windows\\System32\\cmd.exe"},"maxDurationMillis":86400001}""");
        var error = Assert.Throws<HostFailure>(() => service.Acquire(payload.RootElement));
        Assert.Equal("INVALID_DURATION_LIMIT", error.Code);
    }

    [Fact]
    public async Task SuspendsAndExplicitlyResumesOwnedProcess()
    {
        var command = Environment.GetEnvironmentVariable("ComSpec") ?? @"C:\Windows\System32\cmd.exe";
        using var child = Process.Start(new ProcessStartInfo(command, "/c ping -t 127.0.0.1")
        {
            CreateNoWindow = true,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true
        }) ?? throw new InvalidOperationException("Cannot start test process");
        var journal = Path.Combine(Path.GetTempPath(), $"kuudra-process-control-{Guid.NewGuid()}.json");
        var service = new ProcessControlService(journal);
        try
        {
            using var acquire = JsonDocument.Parse(JsonSerializer.Serialize(new
            {
                owner = "test-owner", targets = new Dictionary<string, string> { ["cmd"] = command }, maxDurationMillis = 5_000
            }, JsonOptions.Default));
            service.Acquire(acquire.RootElement);
            using var suspend = JsonDocument.Parse(JsonSerializer.Serialize(new
            {
                owner = "test-owner", target = "cmd", pid = child.Id, durationMillis = 1_000
            }, JsonOptions.Default));
            await service.SuspendAsync(suspend.RootElement);
            Assert.True(File.Exists(journal));
            using var resume = JsonDocument.Parse(JsonSerializer.Serialize(new
            {
                owner = "test-owner", target = "cmd", pid = child.Id
            }, JsonOptions.Default));
            await service.ResumeAsync(resume.RootElement, "EXPLICIT_RESUME");
            Assert.False(File.Exists(journal));
            Assert.False(child.HasExited);
        }
        finally
        {
            if (!child.HasExited) child.Kill(true);
            child.WaitForExit();
            File.Delete(journal);
        }
    }
}
