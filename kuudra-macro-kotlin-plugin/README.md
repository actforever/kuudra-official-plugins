# Kuudra Macro Kotlin Plugin

`actforever/macro-kotlin` registers the `.kt` authoring frontend for `actforever/macro-spec`. It embeds Kotlin 2.4.10 so deployment needs one plugin JAR, and declares an empty component index because it contributes a frontend rather than a Flow component.

Scripts are trusted local Kotlin builder programs. They are compiled at component initialization (and when an executor explicitly reloads a changed file), return `MacroProgramDefinition`, and are not executed for every Event. Dynamic decisions must use `ref("session#value")` and related macro conditions so actual context lookup remains inside the executor.

Compilation resolves its classpath from the dependency-aware plugin ClassLoader rather than the App thread context ClassLoader. Evaluation uses that same loader as its parent, preserving the shared `macro-spec` type identity across `macro-kotlin` and executor plugins. The exposed `KotlinMacroBuilder` is a real receiver DSL; nested condition and loop lambdas build nested IR nodes instead of accidentally appending operations to the outer program.

## Deployment and configuration

Deploy `user-interaction-spec`, `macro-spec`, `macro-kotlin`, and an executor such as `awt-robot`. The Kotlin plugin registers a frontend but no Flow Component. Reference the script from the executor resource:

```yaml
apiVersion: kuudra.io/v1alpha1
kind: EventHandler
metadata: {namespace: macro, name: replay}
spec:
  component: actforever/awt-robot/awt-robot
  desiredState: running
  options:
    script: macros/replay.kt
    maxTotalSteps: 10000
    syntheticMarkerLifetimeMillis: 500
```

Place the source at `<home>/plugins/actforever/awt-robot/macros/replay.kt`. The path belongs to the executor plugin home, not the Kotlin frontend home.

## Script examples

```kotlin
import io.github.actforever.kuudra.interaction.*

macro {
    tap(KeySpec(A, KeyLocation.STANDARD), 50)
    move(ScreenPosition(960, 540, CoordinateSpace.SCREEN))
    click(BUTTON_1)
    wheel(MouseWheelSpec(WheelDirection.UP, 3))
}
```

Live context conditions, bounded loops and cooperative Session cancellation:

```kotlin
macro {
    whenCondition(ref("session#macroEnabled").eq(true), {
        whileCondition(ref("session#cancelRequested").falsy(), 1000) {
            click(BUTTON_1)
            sleep(50, TimeUnit.MILLISECONDS)
        }
    }).otherwise {
        emit("macro.skipped", mapOf("macro" to mapOf("reason" to "disabled")))
        returnMacro()
    }

    whenCondition(ref("session#cancelRequested").truthy(), {
        cancelSession("macro cancelled by context")
    })
}
```

The Kotlin file runs only during compilation to build immutable IR. `ref(...)` stores a context expression in that IR; its value is read later by the executor for each Event.
