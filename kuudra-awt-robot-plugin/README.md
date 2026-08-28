# Kuudra AWT Robot Plugin

`actforever/awt-robot` provides `event-handler/actforever/awt-robot`, a serialized physical-input macro executor based on `java.awt.Robot`. It depends on `actforever/user-interaction-spec` and `actforever/macro-spec`, and accepts the same `KeySpec`, `MouseButtonSpec`, `ScreenPosition`, and `MouseWheelSpec` values emitted by capture plugins.

## Current architecture boundary

The public contracts are decoupled: key/mouse values belong to `user-interaction-spec`, macro representation and frontend SPI belong to `macro-spec`, and Kotlin authoring belongs to `macro-kotlin`. This plugin owns the AWT mapping, shared physical device, held-input cleanup and serialized execution queue.

At present it also owns `MacroProgram`, the generic IR traversal/control-flow executor. It is therefore an AWT-backed executor rather than a pure Driver plugin. A future executor module can move that interpreter above a small public driver interface, allowing AWT Robot and other simulation drivers to share identical execution logic without changing macro files.

## Component options

```yaml
spec:
  component: actforever/awt-robot/awt-robot
  desiredState: running
  options:
    maxTotalSteps: 10000
    syntheticMarkerLifetimeMillis: 500
    steps:
      - action: keyTap
        key:
          code: F24
          location: STANDARD
        holdMillis: 50
```

## Minimal Flow

The Handler needs a SESSION-domain Event, so a source must pass through an Ingress first. With the HelloWorld, default and logging plugins deployed, this safe example only emits a result Event and is suitable for an automated logic check:

The same runnable manifest is stored under `examples/macro-yaml-safe`.

```yaml
apiVersion: kuudra.io/v1alpha1
kind: EventSource
metadata: {namespace: macro-demo, name: trigger}
spec:
  component: kuudra-official/hello-world/hello-world
  desiredState: running
  options: {intervalMillis: 1000}
---
apiVersion: kuudra.io/v1alpha1
kind: Ingress
metadata: {namespace: macro-demo, name: ingress}
spec:
  component: kuudra-official/default/plain-ingress
  desiredState: active
  options: {groupKey: "${event#hello-world.message}"}
---
apiVersion: kuudra.io/v1alpha1
kind: EventHandler
metadata: {namespace: macro-demo, name: robot}
spec:
  component: actforever/awt-robot/awt-robot
  desiredState: running
  options:
    maxTotalSteps: 100
    steps:
      - action: if
        condition: {ref: event#hello-world.message, operator: EQUALS, value: hello-world}
        then:
          - action: emit
            eventType: macro.completed
            data: {macro: {result: yaml-executed}}
---
apiVersion: kuudra.io/v1alpha1
kind: EventHandler
metadata: {namespace: macro-demo, name: logger}
spec:
  component: kuudra-official/logging/event-logger
  desiredState: running
  options:
    level: INFO
    message: "Macro result: ${event#macro.result}"
---
apiVersion: kuudra.io/v1alpha1
kind: Flow
metadata: {namespace: macro-demo, name: replay}
spec:
  imports:
    trigger: {kind: EventSource, name: trigger}
    ingress: {kind: Ingress, name: ingress}
    robot: {kind: EventHandler, name: robot}
    logger: {kind: EventHandler, name: logger}
  edges:
    - {from: trigger, to: ingress}
    - {from: ingress, to: robot}
    - {from: robot, to: logger}
```

For real physical input replace the Handler steps with operations such as:

```yaml
steps:
  - action: keyTap
    key: {code: A, location: STANDARD}
    holdMillis: 50
  - action: mouseMove
    position: {x: 960, y: 540, coordinateSpace: SCREEN}
  - action: mouseClick
    button: {button: BUTTON_1}
    holdMillis: 50
  - action: mouseWheel
    wheel: {direction: UP, amount: 3}
```

Typed objects may come directly from an exact placeholder, which preserves the JSON object instead of converting it to text:

```yaml
- action: keyTap
  key: "${event#user-interaction.key}"
```

Supported actions are `keyPress`, `keyRelease`, `keyTap`, `type`, `mousePress`, `mouseRelease`, `mouseClick`, `mouseMove`, `mouseWheel`, `sleep`, `if`, `loop`, `break`, `return`, `cancelSession`, and `emit`. `type` accepts a `keys` array of `KeySpec` plus optional `modifiers`, `holdMillis`, and `intervalMillis`; it intentionally does not promise arbitrary Unicode text entry.

Conditions use a live context reference, so loops can observe values written after the Handler started:

```yaml
- action: loop
  maxIterations: 100
  while:
    ref: session#macroEnabled
    operator: EQUALS
    value: true
  steps:
    - action: keyTap
      key: { code: A, location: STANDARD }
    - action: sleep
      durationMillis: 25
```

Operators are `TRUTHY`, `FALSY`, `EXISTS`, `NOT_EXISTS`, `EQUALS`, `NOT_EQUALS`, comparisons, `IN`, `NOT_IN`, and `MATCHES_REGEX`. A loop must have `count` or `maxIterations`. `break` exits the nearest loop, `return` ends only this Handler, and `cancelSession` asks Runtime to cooperatively cancel the current Session.

An `emit` step creates a normal Session Event and can copy input data first:

```yaml
- action: emit
  eventType: macro.completed
  copyInputData: true
  data:
    macro:
      status: completed
```

All configured Handler resources share one fair physical Robot queue. Repeated presses are idempotent. Pausing releases physically held input and reacquires the logical held set on resume; cancellation, failure, stop, and plugin destruction release every input owned by the macro in reverse order. Abrupt operating-system process termination remains outside Java's cleanup guarantee.

Robot input is registered with the shared interaction contract before injection. JNativeHook drops matching recaptured events by default; `syntheticEventPolicy: EMIT` emits them with `user-interaction.synthetic: true` for diagnostics.

## Kotlin macro source

Install `actforever/macro-kotlin` beside the contract and AWT plugins, then configure `script` instead of `steps`:

```yaml
options:
  script: macros/hello.kt
  maxTotalSteps: 10000
  syntheticMarkerLifetimeMillis: 500
```

The relative path is resolved inside `<plugin-home>/macros`; absolute paths, traversal and symbolic-link escapes are rejected. A resource must configure exactly one of `steps` and `script`. The source is compiled during component initialization and recompiled on a later `STOPPED -> RUNNING` transition only when its size or modification time changed; it is never evaluated per Event.

```kotlin
macro {
    press(A)
    sleep(100)
    release(A)

    whenCondition(ref("session#enabled").eq(true), {
        whileCondition(ref("session#cancelled").falsy(), 1000) {
            click(BUTTON_1)
            sleep(50)
        }
    }).otherwise {
        emit("macro.skipped", "disabled")
    }
}
```

Kotlin is a trusted local authoring frontend, not an Event-time sandbox. The frontend executes only while compiling the file and must return `macro { ... }`; it produces the same immutable, language-neutral IR as YAML. Runtime conditions are represented by `ref(...)`, evaluated against the live Event/Session/Flow/Global contexts, and therefore keep the same pause/cancel/checkpoint semantics as YAML. `.groovy` and `.kmd` are reserved for future independent frontends and are not accepted unless a corresponding plugin registers them.
