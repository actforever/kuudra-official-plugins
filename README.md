# Kuudra Plugins

本仓库存放 Kuudra 的可部署插件、共享业务规约和端到端配置示例。它与 Kuudra 内核仓库分离：内核只提供插件、事件流、会话和调谐基础设施，不会隐式注册这里的任何组件。

## 版本与运行环境

- Java 17 或更高版本；
- 当前插件基于 Kuudra `v0.4.4`；
- 仓库自身当前为 `0.1.0-SNAPSHOT` 开发版本；
- 插件身份始终是 `namespace/pluginId`，组件引用格式为 `plugin-namespace/plugin-id/component-name`。

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
- Flow 负责路由，Ingress 负责建立 Session，SessionCoordinationPolicy 负责调度与依赖关系。

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
2. 在 `<home-directory>/manifests` 中声明组件资源和 Flow；
3. 在 `config.yaml` 中激活清单使用的资源命名空间；
4. 启动 Kuudra，并通过 App/Web API 查询插件、组件文档、资源调谐状态和 Session。

加载插件本身不会构建组件。只有资源清单显式声明组件，并且资源命名空间被选中时，Kuudra 才会创建和调谐实例。资源由 `kind/namespace/name` 唯一标识；同一资源被多个 Flow 导入时共享同一个实例。

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
- 组件使用结构化 `@ComponentDoc`、`@SpecProperty` 和 `@EventEmission` 描述用途、配置及输出；
- 插件通过 `PluginLogger` 记录日志，不直接绑定 SLF4J/Logback；
- 组件配置使用 `PluginComponentContext.configuration(..., Class<T>, defaultValue)` 完成统一类型转换；
- 修改组件契约时，同步更新模块 README、组件文档注解和相关 `examples/` 清单。

各模块的配置参数、事件结构和限制以模块 README 及 Kuudra 暴露的 ComponentTemplate 文档 API 为准。
