# User interaction → logging

This example captures global keyboard press/release events through `actforever/jnativehook`, creates a Session through the official plain Ingress, and prints the neutral event through the logging plugin.

Deploy these plugin archives to `<home-directory>/plugins`:

- `kuudra-user-interaction-spec-plugin-0.2.0-alpha-1.jar`
- `kuudra-jnativehook-plugin-0.2.0-alpha-1.jar`
- `kuudra-default-plugin-0.2.0-alpha-1.jar`
- `kuudra-logging-plugin-0.2.0-alpha-1.jar`

The spec plugin must remain a separate archive. The JNativeHook metadata declares it as a mandatory dependency, which makes both plugins use the same `KeySpec` class at runtime. JNativeHook itself is shaded into the JNativeHook plugin; do not copy a standalone `jnativehook-2.2.2.jar` into Kuudra's strict plugin directory.

Copy the manifests and AbilityProfile into the matching home directories, merge the example `config.yaml`, and start Kuudra. Pressing and releasing `A` should produce messages similar to:

```text
Keyboard PRESSED: A
Keyboard RELEASED: A
```

Mouse motion is opt-in. Declare a Resource only when motion events are needed, with optional static configuration:

```yaml
apiVersion: kuudra.io/v1alpha2
kind: EventSource
metadata: {namespace: macro, name: mouse-motion}
spec:
  template: actforever/jnativehook/jnativehook-mouse-motion
  options:
    output:
      strategy: COALESCE
      intervalMillis: 16
```
