# Kuudra AWT Robot Plugin

`actforever/awt-robot` provides `event-handler/actforever/awt-robot`, a serialized physical-input macro executor based on `java.awt.Robot`. It depends on `actforever/user-interaction-spec` and accepts the same `KeySpec`, `MouseButtonSpec`, `ScreenPosition`, and `MouseWheelSpec` values emitted by capture plugins.

## Component options

```yaml
spec:
  component: event-handler/actforever/awt-robot
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
