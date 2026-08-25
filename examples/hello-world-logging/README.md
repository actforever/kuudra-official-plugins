# HelloWorld → Logging 最小闭环

本示例连接两个官方插件组件：

```text
kuudra-official/hello-world
  -> ingress/kuudra-official/plain-ingress
  -> kuudra-official/event-logger
```

EventSource 输出的是 RAW 域事件，而 EventHandler 运行在 SESSION 域，所以 Ingress 是必要边界。外置 `kuudra-default-plugin` 提供 `ingress/kuudra-official/plain-ingress`；只有本示例声明 `kind: Ingress` 资源时才创建实例。它无条件准入 Event 并计算 `groupKey`；Session 创建、租约和串行调度由 Runtime 管理。

先构建插件，然后把三个 JAR 放入 `<home-directory>/plugins/`：

```powershell
mvn clean package
Copy-Item kuudra-hello-world-plugin/target/kuudra-hello-world-plugin-0.1.0-SNAPSHOT.jar <home-directory>/plugins/
Copy-Item kuudra-logging-plugin/target/kuudra-logging-plugin-0.1.0-SNAPSHOT.jar <home-directory>/plugins/
Copy-Item kuudra-default-plugin/target/kuudra-default-plugin-0.1.0-SNAPSHOT.jar <home-directory>/plugins/
```

将 `manifests/` 下四个 YAML 文件复制到 `<home-directory>/manifests/` 后启动 Kuudra。默认每秒产生一个 `hello-world.tick`，Ingress 按消息值分组并串行调度，Logging EventHandler 最终通过插件 Logger、SystemEventBus 和 `kuudra-logging` 输出：

```text
[plugin=kuudra-official/logging] received hello-world
```
