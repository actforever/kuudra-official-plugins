# JNativeHook Plugin

`actforever/jnativehook` captures global keyboard and mouse input and emits the platform-neutral values supplied by `actforever/user-interaction-spec`.

Components:

- `event-source/actforever/jnativehook-keyboard`
- `event-source/actforever/jnativehook-mouse-button`
- `event-source/actforever/jnativehook-mouse-motion`
- `event-source/actforever/jnativehook-mouse-wheel`

The plugin is intentionally a capture layer only. Filtering belongs to an EventAdapter; gesture and sequence state belongs to an EventInterpreter.

Every source accepts `syntheticEventPolicy`. Its default `DROP` suppresses input recently registered by an in-process simulator such as `actforever/awt-robot`; `EMIT` keeps the Event and writes `user-interaction.synthetic: true`. Physical events carry `synthetic: false`. Correlation is bounded and best-effort because native hook APIs do not expose one portable injection identity on every platform.

Mouse motion is disabled when no `jnativehook-mouse-motion` resource is declared. When declared, its output strategy is configured below `spec.options.output`:

```yaml
output:
  strategy: COALESCE
  intervalMillis: 16
```

`COALESCE` preserves the leading and latest positions, `THROTTLE` retains only leading positions, and `UNLIMITED` emits every native motion event.
