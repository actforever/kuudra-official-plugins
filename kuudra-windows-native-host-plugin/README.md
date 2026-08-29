# Windows Native Host

`actforever/windows-native-host` 是 Windows 原生能力父插件。它不提供 Flow Component，而是向声明依赖的插件导出强类型 Java API，并管理随 JAR 发布的 C# sidecar。

当前能力：

- 在首个特权 Component 初始化时通过 UAC 启动一个管理员 broker；仅加载本插件不会弹出 UAC；
- 通过限制为当前用户 SID 的随机 Named Pipe 通信，并双向核对 JVM/broker PID；
- 使用带长度上限和主版本协商的 JSON 帧，不接受 PowerShell、shell 或任意方法名；
- 按 Component owner 注册进程白名单、最大持续时间和活动操作；
- 在 JVM 断开后继续执行 deadline 恢复，并在下次启动时补偿恢复日志。

管理员 broker 只提供 `ACQUIRE_PROCESS_CONTROL`、`SUSPEND`、`RESUME`、`RESTORE_OWNER`、`HELLO` 和 `SHUTDOWN`。未来防火墙/适配器能力可以新增强类型 RPC；Overlay 必须使用另一个普通权限 UI sidecar，不能进入管理员进程。

## 与 Kuudra 和下级插件的连接

本插件的 `components.idx` 为空，因此它不直接出现在 Flow 中。Kuudra 根据 `metadata.toml` 创建 `WindowsNativeHostPlugin`，父插件在 `initialize(PluginContext)` 中把唯一 `NativeHostProvider` 安装到 `WindowsNativeHost` 强类型门面。下级插件必须在自己的 metadata 中声明对 `actforever/windows-native-host` 的依赖；Kuudra 校验版本范围后把父插件 ClassLoader 链接给下级，使双方共享同一套 `WindowsNativeHost`、DTO 和 lease 类型。

`actforever/process-control` 是当前下级插件。Kuudra 仍负责发现并实例化它的 EventHandler、传入 `PluginComponentContext`、调谐 start/stop，并把 `KuudraEvent` 与 `ActionContext` 交给 Handler。Handler 初始化时才调用 `WindowsNativeHost.acquireProcessControl(...)`，以组件 canonical reference 创建隔离 owner，并获得 `ProcessControlLease`。C# broker 不接收 Kuudra Event/Context，也不参与 Flow 或 Session 调度。

```text
Kuudra App / PluginManager
  ├─ windows-native-host ClassLoader ─ WindowsNativeHost API
  └─ process-control ClassLoader ─────┘
                    │ typed capability / owner lease
                    ▼
             NativeHostProvider (Java/JNA)
                    │ command pipe + event pipe
                    ▼
        Kuudra.Windows.PrivilegedHost.exe (.NET 8)
                    │ fixed Win32 operations
                    ▼
       OpenThread / SuspendThread / ResumeThread
```

## C# 执行链路

Maven 执行 `dotnet publish -r win-x64 --self-contained true`，再把单文件 EXE 和 SHA-256 嵌入 JAR。首次获准申请特权能力时，Java 校验并解压 EXE，通过 JNA `ShellExecuteEx` 的 `runas` verb 启动它；只加载 JAR 不会解压或提权。

启动参数只有两个随机 pipe 名、期望 JVM PID 和恢复日志路径。broker 为当前 SID 创建显式 ACL；command pipe 严格串行承载 request/response，event pipe 只承载异步完成事件。两条 pipe 都由 C# 的 `GetNamedPipeClientProcessId` 和 Java 的 `GetNamedPipeServerProcessId` 双向核对进程身份，随后再用 `HELLO` 协商协议主版本。帧格式为 4 字节大端长度加 UTF-8 JSON，最大 64 KiB。

command executor 只做顺序收发和 request UUID 校验，event reader 只处理 `PROCESS_OPERATION_COMPLETED`；业务 Future 回调切离 I/O 线程，避免生命周期回调重入传输线程。协议操作是固定枚举，不接受脚本、类名、方法名或任意命令。

broker 在执行 `SUSPEND` 前按白名单路径、可选 PID、进程启动时间和镜像路径重新验证目标，拒绝同一进程重叠暂停；逐线程暂停发生部分失败时会立即回滚。成功后先写恢复日志，再返回 operation/deadline。自然到期、显式恢复或 owner 清理会恢复线程、删除日志并通过 event pipe 完成 Java `CompletionStage`。

更完整的生命周期、故障矩阵和设计理由见核心仓库 `docs/kuudra-windows-native-host.md`。

## 构建与部署

构建机需要 Java 17、Maven 和 .NET 8 SDK：

```powershell
mvn -pl kuudra-windows-native-host-plugin -am clean package -DskipTests=false
```

Maven 会先运行 Java 与 C# 测试，再执行 `dotnet publish --runtime win-x64 --self-contained true`，把单文件 EXE 和 SHA-256 放入插件 JAR 的 `META-INF/native/win-x64/`。目标机器不需要安装 .NET。

运行时 EXE 经哈希校验后解压到 `<plugin-home>/native/0.1.0/win-x64/`。活动操作恢复日志位于 `<plugin-home>/state/active-process-operations.json`。

## 恢复边界

正常到期、显式恢复、Session 取消、Component/App 停止和 JVM 退出均有恢复路径。若管理员 broker 自身被强杀或机器断电，无法保证立即恢复；再次启动 Kuudra 会先读取恢复日志。进程仍被挂起时，可在任务管理器结束目标进程，或重新启动 Kuudra 并批准 UAC 以执行补偿。
