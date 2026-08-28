# Kotlin 宏安全黑箱示例

该示例验证真实 `HelloWorld EventSource -> plain Ingress -> AWT Robot EventHandler -> logging EventHandler` 链路。宏不注入真实键鼠输入，只根据 Event 运行动态条件并 `emit` 一个新 Event，因此适合自动化黑箱验证。

需要部署七个插件 JAR：`hello-world`、`default`、`logging`、`user-interaction-spec`、`macro-spec`、`macro-kotlin` 和 `awt-robot`。把 `manifests.yaml` 复制到 `<home>/manifests/`，把 `safe-emit.kt` 复制到 `<home>/plugins/actforever/awt-robot/macros/`。

启动后应周期看到：

```text
Kotlin macro emitted compiled-and-executed
```

这条日志同时证明外部 `.kt` 已在组件初始化时编译成宏 IR、运行时 `event#hello-world.message` 查询成功、Handler 输出沿当前 Session 路由到下游日志组件。测试窗口内不应出现 `unexpected-input`；若两个分支同时出现，说明嵌套 lambda 没有形成正确的 Kotlin receiver DSL。
