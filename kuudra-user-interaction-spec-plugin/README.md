# User Interaction Spec Plugin

`actforever/user-interaction-spec` defines platform-neutral keyboard and mouse values shared by input capture and input simulation plugins.

The plugin registers no runtime components. Dependents declare it as a mandatory plugin dependency so typed values are resolved through the same plugin ClassLoader.

It also provides the bounded process-local `InjectedInteractionRegistry`. Simulation plugins register an expected platform-neutral interaction before injection; capture plugins consume the matching marker to drop or label recaptured synthetic input. The declared dependency ensures both plugin ClassLoaders resolve the same registry classes.

```yaml
key:
  code: A
  location: STANDARD
```

## Common values

The contract is deliberately independent from JNativeHook and AWT. Capture plugins translate platform events into these values, while simulation plugins translate the same values into their own driver calls.

```yaml
key: {code: LEFT_SHIFT, location: LEFT}
button: {button: BUTTON_1}
position: {x: 960, y: 540, coordinateSpace: SCREEN}
wheel: {direction: UP, amount: 3}
```

This plugin has no Component resource and therefore does not appear in a Flow. Deploy its JAR beside every dependent capture, macro and driver plugin. Dependency-aware class loading guarantees that all dependents use the same value and registry class identities.

At runtime the same value can be restored with:

```java
KeySpec key = event.data().get(InteractionEvents.DATA_NAMESPACE, InteractionEvents.KEY, KeySpec.class);
```
