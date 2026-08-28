# Process Control Plugin

`actforever/process-control` 提供 `event-handler/actforever/process-control/process-control`，通过 `actforever/windows-native-host` 对清单明确授权的 Windows 进程执行限时暂停和恢复。

```yaml
apiVersion: kuudra.io/v1alpha1
kind: EventHandler
metadata: {namespace: process-demo, name: process-control}
spec:
  component: actforever/process-control/process-control
  desiredState: running
  options:
    allowElevation: true
    targets:
      gta:
        executablePath: 'D:\Games\Grand Theft Auto V\GTA5.exe'
    action: '${event#process.action}'
    target: '${event#process.target}'
    pid: '${event#process.pid}'
    durationMillis: '${event#process.durationMillis}'
    defaultDurationMillis: 10000
    maxDurationMillis: 60000
```

- `allowElevation` 必须静态写为 `true`，否则初始化在弹 UAC 前失败；
- `targets` 是静态白名单，路径必须绝对、存在且不能包含占位符；
- `action` 仅接受 `SUSPEND`/`RESUME`；`target` 只能是白名单别名；
- 同一路径只有一个运行实例时可省略 `pid`，多实例时必须传 PID，broker 会再次核对映像路径和进程创建时间；
- `defaultDurationMillis` 默认 10 秒；`maxDurationMillis` 默认 60 秒，可由可信清单配置到 24 小时；
- 同一进程不叠加 suspension lease。重复暂停会失败；恢复不存在的 lease 幂等成功。

`SUSPEND` 的 CompletionStage 直到 deadline、显式恢复、目标退出或取消清理后才完成，因此 Session lease 覆盖完整副作用周期。Kuudra pause 不取消或延长 deadline；Session cancel、Component stop/destroy 和 App stop 会提前恢复。

Windows 的 `SuspendThread`/`ResumeThread` 主要面向调试器，无法选择安全暂停点。请先对可丢弃的测试进程验证；对游戏、反作弊保护进程或生产程序操作可能造成不稳定，也可能受第三方软件条款限制。
