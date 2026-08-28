# Windows Native Host

`actforever/windows-native-host` 是 Windows 原生能力父插件。它不提供 Flow Component，而是向声明依赖的插件导出强类型 Java API，并管理随 JAR 发布的 C# sidecar。

当前能力：

- 在首个特权 Component 初始化时通过 UAC 启动一个管理员 broker；仅加载本插件不会弹出 UAC；
- 通过限制为当前用户 SID 的随机 Named Pipe 通信，并双向核对 JVM/broker PID；
- 使用带长度上限和主版本协商的 JSON 帧，不接受 PowerShell、shell 或任意方法名；
- 按 Component owner 注册进程白名单、最大持续时间和活动操作；
- 在 JVM 断开后继续执行 deadline 恢复，并在下次启动时补偿恢复日志。

管理员 broker 只提供 `ACQUIRE_PROCESS_CONTROL`、`SUSPEND`、`RESUME`、`RESTORE_OWNER`、`HELLO` 和 `SHUTDOWN`。未来防火墙/适配器能力可以新增强类型 RPC；Overlay 必须使用另一个普通权限 UI sidecar，不能进入管理员进程。

## 构建与部署

构建机需要 Java 17、Maven 和 .NET 8 SDK：

```powershell
mvn -pl kuudra-windows-native-host-plugin -am clean package -DskipTests=false
```

Maven 会先运行 Java 与 C# 测试，再执行 `dotnet publish --runtime win-x64 --self-contained true`，把单文件 EXE 和 SHA-256 放入插件 JAR 的 `META-INF/native/win-x64/`。目标机器不需要安装 .NET。

运行时 EXE 经哈希校验后解压到 `<plugin-home>/native/0.1.0/win-x64/`。活动操作恢复日志位于 `<plugin-home>/state/active-process-operations.json`。

## 恢复边界

正常到期、显式恢复、Session 取消、Component/App 停止和 JVM 退出均有恢复路径。若管理员 broker 自身被强杀或机器断电，无法保证立即恢复；再次启动 Kuudra 会先读取恢复日志。进程仍被挂起时，可在任务管理器结束目标进程，或重新启动 Kuudra 并批准 UAC 以执行补偿。
