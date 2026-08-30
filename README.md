# Kuudra Plugins

> Windows 原生能力：`kuudra-windows-native-host-plugin`（`actforever/windows-native-host`）管理受控 C# sidecar、UAC 与 Named Pipe；`kuudra-process-control-plugin`（`actforever/process-control`）提供含 `suspend`/`resume` 具名入口的 Controller。安全验证见 [`examples/process-control-safe`](examples/process-control-safe/README.md)。父插件本身不发布 ResourceTemplate，只有实际 claim 特权 Resource 时才弹 UAC。

本仓库存放 Kuudra 的可部署插件、共享业务规约和端到端配置示例。它与 Kuudra 内核仓库分离：内核只提供插件、事件流、会话和调谐基础设施，不会隐式注册这里的任何组件。

## 版本与运行环境

- Java 17 或更高版本；
- 当前插件基于 Kuudra `v0.5.0-alpha-1`；
- 仓库自身当前为 `0.2.0-alpha-1`；
- 插件身份始终是 `namespace/pluginId`，ResourceTemplate 引用格式为 `plugin-namespace/plugin-id/template-name`。

插件 JAR 统一部署到 `<home-directory>/plugins`。Kuudra 会严格加载目录中的每个 JAR，普通依赖 JAR、损坏归档或依赖不合法的插件都会使启动失败。

## 模块

| 模块 | 插件身份 | 职责 |
| --- | --- | --- |
| `kuudra-default-plugin` | `kuudra-official/default` | `plain-ingress`、`plain-egress` 和系统控制 Handler |
| `kuudra-conditional-boundary-plugin` | `kuudra-official/conditional-boundary` | 基于上下文条件的 Ingress/Egress |
| `kuudra-hello-world-plugin` | `kuudra-official/hello-world` | 最小周期事件源示例 |
| `kuudra-logging-plugin` | `kuudra-official/logging` | 将 Event 写入插件日志通道 |
| `kuudra-session-probe-plugin` | `kuudra-official/session-probe` | 会话调度、依赖和协作控制探针 |
| `kuudra-user-interaction-spec-plugin` | `actforever/user-interaction-spec` | 平台无关的键盘、鼠标值对象和合成输入关联规约 |
| `kuudra-jnativehook-plugin` | `actforever/jnativehook` | 全局键盘和鼠标捕获驱动 |
| `kuudra-awt-robot-plugin` | `actforever/awt-robot` | 基于 AWT Robot 的输入模拟驱动 |
| `kuudra-macro-spec-plugin` | `actforever/macro-spec` | 与具体驱动无关的宏动作模型 |
| `kuudra-macro-kotlin-plugin` | `actforever/macro-kotlin` | 受限 Kotlin 宏脚本编译与执行 |

`kuudra-official` 面向通用 Kuudra 基础组件；`actforever` 下的模块是建立在内核之上的业务插件，不应被误认为内核内置能力。

## 分层关系

```text
user-interaction-spec   macro-spec
        ^                  ^
        |                  |
   jnativehook        macro-kotlin
        |                  |
        +-- Event/Flow ----+
               |
           awt-robot
```

- 捕获层只产生平台无关事件；
- 宏规约描述动作，不依赖 AWT 或 JNativeHook；
- 驱动层将标准动作转换成具体平台调用；
- 插件依赖保证共享 POJO 由同一个依赖 ClassLoader 提供；
- Ability 负责 claim、路由和控制；Ingress 节点通过 CREATE/JOIN 建立或加入 Session，并就地声明调度与依赖。

## 构建

先在 Kuudra 内核仓库安装 API 和插件开发接口：

```powershell
mvn -pl kuudra-api,kuudra-plugin -am install -DskipTests
```

然后在本仓库执行：

```powershell
mvn clean package
mvn test -DskipTests=false
```

也可以只构建目标插件及其 reactor 依赖：

```powershell
mvn -pl kuudra-jnativehook-plugin -am clean package
```

每个插件的 `target/` 中会生成可部署 JAR。带共享规约依赖的插件必须同时部署规约插件，不能只复制业务插件本身。

## 部署与配置

1. 将需要的插件 JAR 复制到 `<home-directory>/plugins`；
2. 在 `<home-directory>/manifests` 中声明 Resource 和 Ability；
3. 在 `<home-directory>/ability-profiles` 声明 Profile，并在 `config.yaml` 的 `ability-profiles` 中选择；
4. 启动 Kuudra，并通过 App/Web API 查询插件、ResourceTemplate、Ability、Resource 和 Session。

加载插件本身不会构建 Resource。只有有效 Ability claim 才会物化并调谐实例。Resource 由 `kind/namespace/name` 唯一标识；被多个 Ability claim 时共享同一个实例。

## 可运行示例

| 示例 | 内容 |
| --- | --- |
| [`hello-world-logging`](examples/hello-world-logging/README.md) | 周期事件源经 plain Ingress 输出到日志 |
| [`user-interaction-logging`](examples/user-interaction-logging/README.md) | 捕获键盘事件并记录标准化数据 |
| [`macro-yaml-safe`](examples/macro-yaml-safe/README.md) | 使用 YAML 动作模型安全执行宏 |
| [`macro-kotlin-safe`](examples/macro-kotlin-safe/README.md) | 使用受限 Kotlin 脚本执行宏 |
| [`session-dependency`](examples/session-dependency/README.md) | 验证 Session 调度和依赖终止关系 |

复制示例前请阅读对应 README，确保部署其列出的所有插件 JAR。资源清单使用的 namespace 也必须进入当前 Kuudra 实例的激活集合。

## 开发约定

- 插件元数据位于 `META-INF/kuudra-plugin/metadata.toml`；
- 插件版本使用不带 `v` 的点分数字格式，可附加 prerelease/build 后缀；
- 依赖必须声明 namespace、pluginId、mandatory 和 versionRange；
- Resource 使用结构化 `@ResourceDoc`、`@SpecProperty` 和 `@EventEmission` 描述用途、options、arguments 及输出；
- 插件通过 `PluginLogger` 记录日志，不直接绑定 SLF4J/Logback；
- Resource 静态配置使用 `ResourceContext.option(...)`，Controller 动态参数使用 `EventHandlerContext.arguments()`；
- 修改组件契约时，同步更新模块 README、组件文档注解和相关 `examples/` 清单。

各模块的配置参数、事件结构和限制以模块 README 及 Kuudra 暴露的 ResourceTemplate API 为准。
