# Process Control 安全验证

本示例暂停一个本地 `ping.exe` 两秒，用于验证 UAC、进程身份校验、deadline 和恢复，不依赖游戏或在线服务。

1. 构建并部署 `windows-native-host`、`process-control`、`session-probe` 和 `default` 四个插件 JAR。
2. 在另一个终端运行：

   ```powershell
   ping -t 127.0.0.1
   ```

3. 将 `manifests.yaml` 放入 `<home>/manifests/`，并在根配置中选择 `process-control-demo` namespace。
4. 启动 Kuudra 并批准一次 UAC。五秒后，`ping` 输出应暂停约两秒后自动继续。
5. 将 `durationMillis` 临时改长，重启后在暂停期间按 Ctrl-C 关闭 Kuudra，确认 `ping` 被提前恢复。

如果系统目录不是 `C:\Windows`，请把清单路径改为 `$env:SystemRoot\System32\PING.EXE` 展开后的绝对路径；清单本身不展开环境变量。
