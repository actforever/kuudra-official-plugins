# HelloWorld → Logging 最小闭环

本示例连接两个官方插件组件：

```text
kuudra-official/hello-world
  -> ingress/kuudra-official/plain-ingress
  -> kuudra-official/event-logger
```

EventSource 输出 RAW Event，而 Controller 运行在 SESSION 域，所以 Ingress 是必要边界。Ability 的 Ingress 节点用 CREATE 建立 Session，并就地声明串行调度。

先构建插件，然后把三个 JAR 放入 `<home-directory>/plugins/`：

```powershell
mvn clean package
Copy-Item kuudra-hello-world-plugin/target/kuudra-hello-world-plugin-0.2.0-alpha-1.jar <home-directory>/plugins/
Copy-Item kuudra-logging-plugin/target/kuudra-logging-plugin-0.2.0-alpha-1.jar <home-directory>/plugins/
Copy-Item kuudra-default-plugin/target/kuudra-default-plugin-0.2.0-alpha-1.jar <home-directory>/plugins/
```

将 `manifests/` 复制到 home 的同名目录、将 `ability-profiles/` 复制到 home，并把示例 `config.yaml` 合并到 home 配置后启动。Logging Controller 的 `log` 入口最终输出：

```text
[plugin=kuudra-official/logging] received hello-world
```
