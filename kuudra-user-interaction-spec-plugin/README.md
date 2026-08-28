# User Interaction Spec Plugin

`actforever/user-interaction-spec` defines platform-neutral keyboard and mouse values shared by input capture and input simulation plugins.

The plugin registers no runtime components. Dependents declare it as a mandatory plugin dependency so typed values are resolved through the same plugin ClassLoader.

It also provides the bounded process-local `InjectedInteractionRegistry`. Simulation plugins register an expected platform-neutral interaction before injection; capture plugins consume the matching marker to drop or label recaptured synthetic input. The declared dependency ensures both plugin ClassLoaders resolve the same registry classes.

```yaml
key:
  code: A
  location: STANDARD
```

At runtime the same value can be restored with:

```java
KeySpec key = event.data().get(InteractionEvents.DATA_NAMESPACE, InteractionEvents.KEY, KeySpec.class);
```
