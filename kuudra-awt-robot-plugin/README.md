# Kuudra AWT Robot Plugin

`actforever/awt-robot` provides `event-handler/actforever/awt-robot`, a serialized physical-input macro executor based on `java.awt.Robot`. It depends on `actforever/user-interaction-spec` and `actforever/macro-spec`, and accepts the same `KeySpec`, `MouseButtonSpec`, `ScreenPosition`, and `MouseWheelSpec` values emitted by capture plugins.

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
