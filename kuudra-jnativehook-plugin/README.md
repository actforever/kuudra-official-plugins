# Kuudra JNativeHook Plugin

`actforever/jnativehook` 使用 JNativeHook 捕获系统级键盘和鼠标输入，并将其转换为 `actforever/user-interaction-spec` 定义的平台无关事件。

本插件只负责“捕获与标准化”：事件过滤和静态重映射应交给 `EventAdapter`，组合键、连击、手势和时序状态机应交给 `EventInterpreter`，输入回放应交给 `actforever/awt-robot` 等驱动插件。

## 前置条件

- Kuudra 内核 `v0.4.4`、Java 17 或更高版本；
- 必须同时部署 `actforever/user-interaction-spec` 插件；
- 桌面环境必须允许进程注册全局输入 Hook。部分 Linux 桌面可能需要额外的原生库、设备权限或 X11 会话支持。

`user-interaction-spec` 是强制插件依赖。依赖感知的 ClassLoader 会确保捕获端和执行端使用相同的 `KeySpec`、`MouseButtonSpec` 等类型。

## 提供的组件

| 资源引用 | 事件 | 说明 |
| --- | --- | --- |
| `actforever/jnativehook/jnativehook-keyboard` | `user-interaction.keyboard.pressed`、`user-interaction.keyboard.released` | 全局键盘按下和释放 |
| `actforever/jnativehook/jnativehook-mouse-button` | `user-interaction.mouse-button.pressed`、`user-interaction.mouse-button.released` | 鼠标按钮按下和释放，不合成 click |
| `actforever/jnativehook/jnativehook-mouse-motion` | `user-interaction.mouse-motion.moved`、`user-interaction.mouse-motion.dragged` | 鼠标移动和拖动，支持限流 |
| `actforever/jnativehook/jnativehook-mouse-wheel` | `user-interaction.mouse-wheel.scrolled` | 鼠标滚轮方向和滚动量 |

四个组件均为带暂停能力的 `EventSource`，支持 `running`、`paused` 和 `stopped` desiredState。它们共享一个进程级原生 Hook，并各自注册监听器；未声明的资源不会被创建，也不会监听对应输入。每种组件在 exclusivity domain 内最多只能声明一个实例。

## 构建与安装

```powershell
# 在 Kuudra 内核仓库安装开发接口
mvn -pl kuudra-api,kuudra-plugin -am install -DskipTests

# 在本仓库构建插件及其规约依赖
mvn -pl kuudra-user-interaction-spec-plugin,kuudra-jnativehook-plugin -am clean package
```

将下面两个 JAR 放入 `<home-directory>/plugins`：

```text
kuudra-user-interaction-spec-plugin-*.jar
kuudra-jnativehook-plugin-*.jar
```

JNativeHook 运行库已经被 shade 到插件 JAR 中。Kuudra 会严格解析插件目录中的每个 JAR，因此不要再放入独立的 `jnativehook-*.jar`。

## 最小键盘资源

```yaml
apiVersion: kuudra.io/v1alpha1
kind: EventSource
metadata:
  namespace: macro
  name: keyboard
spec:
  component: actforever/jnativehook/jnativehook-keyboard
  desiredState: running
  options:
    syntheticEventPolicy: DROP
```

资源命名空间 `macro` 必须包含在 Kuudra 的激活命名空间中。完整的 `EventSource -> Ingress -> EventHandler` 配置见 [`examples/user-interaction-logging`](../examples/user-interaction-logging/README.md)。

## 组件配置

所有事件源都支持：

| 路径 | 类型 | 默认值 | 可选值 | 含义 |
| --- | --- | --- | --- | --- |
| `spec.options.syntheticEventPolicy` | String | `DROP` | `DROP`、`EMIT` | 如何处理同进程输入模拟器登记过的回灌事件 |

`DROP` 会丢弃匹配到的合成输入，适合宏监听和回放同时运行的场景；`EMIT` 会保留事件，并写入 `user-interaction.synthetic: true`。物理输入为 `false`。该关联机制有界且为 best-effort，因为不同平台的原生 Hook 并不总能提供稳定的注入标识。

鼠标移动事件源还支持：

```yaml
apiVersion: kuudra.io/v1alpha1
kind: EventSource
metadata:
  namespace: macro
  name: mouse-motion
spec:
  component: actforever/jnativehook/jnativehook-mouse-motion
  desiredState: running
  options:
    syntheticEventPolicy: DROP
    output:
      strategy: COALESCE
      intervalMillis: 16
```

| 策略 | 行为 |
| --- | --- |
| `COALESCE` | 输出时间窗口中的首个位置和最后一个位置，默认策略 |
| `THROTTLE` | 每个窗口只输出首个位置 |
| `UNLIMITED` | 输出每个原生移动事件，`intervalMillis` 被忽略 |

`intervalMillis` 在有限策略下必须大于 0。鼠标移动事件频率很高，除非确实需要逐点轨迹，否则不建议使用 `UNLIMITED`。

## 事件数据模型

事件数据分为两个 namespace：

- `user-interaction`：平台无关的稳定业务字段，Flow 和其他插件应优先读取这里；
- `jnativehook`：原生事件诊断字段，可能受 JNativeHook 和平台差异影响。

键盘按下事件的等价结构如下：

```yaml
type: user-interaction.keyboard.pressed
data:
  user-interaction:
    key: {code: A, location: STANDARD}
    phase: PRESSED
    modifiers: []
    synthetic: false
  jnativehook:
    eventId: 2401
    when: 1710000000000
    modifiers: 0
    keyCode: 30
    keyLocation: 1
```

| 事件类型 | `user-interaction` 字段 |
| --- | --- |
| keyboard pressed/released | `key`、`phase`、`modifiers`、`synthetic` |
| mouse-button pressed/released | `button`、`position`、`phase`、`modifiers`、`synthetic` |
| mouse-motion moved/dragged | `position`、`phase`、`modifiers`、`synthetic` |
| mouse-wheel scrolled | `wheel`、`position`、`phase`、`modifiers`、`synthetic` |

Flow 中可直接使用占位符：

```yaml
message: "Key ${event#user-interaction.key.code} is ${event#user-interaction.phase}"
```

插件代码可通过类型转换接口恢复共享 POJO：

```java
KeySpec key = event.data().get(
        InteractionEvents.DATA_NAMESPACE,
        InteractionEvents.KEY,
        KeySpec.class);
```

## 生命周期与共享

- `start`：获取共享 Hook 租约并注册当前组件监听器；
- `pause`：注销监听器并保留实例；鼠标移动组件会丢弃未输出的采样缓存；
- `resume`：重新注册监听器，从干净的采样窗口继续；
- `stop`：注销监听器、清理采样任务并释放租约；最后一个租约释放后才注销原生 Hook。

组件声明为线程安全，可被多个 Flow 共享同一个资源实例。Flow alias 只是绑定名称，不会复制实例；fan-out 应从同一 alias 声明多条 outgoing edge。

## 常见问题

### 启动时报原生 Hook 注册失败

确认进程运行在可交互桌面会话中并具备输入设备访问权限。错误会通过组件调谐状态和 Kuudra 日志暴露，组件不会在注册失败后伪装为 `RUNNING`。

### 宏回放导致事件循环

保持捕获端和模拟端都依赖同一个 `user-interaction-spec` 插件，并使用默认的 `syntheticEventPolicy: DROP`。`EMIT` 主要用于诊断。

### 鼠标移动没有事件

鼠标移动按需启用。必须显式声明并启动 `jnativehook-mouse-motion` 资源，再将它导入 Flow；仅加载插件 JAR 不会自动创建组件。
