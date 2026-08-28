# Kuudra Macro Spec Plugin

`actforever/macro-spec` defines the language-neutral immutable macro IR, its YAML codec, builder API, conditions and frontend registry. It contains no Flow component and performs no device I/O. Authoring frontends produce `MacroProgramDefinition`; executor plugins consume it, so YAML, Kotlin and future languages share one execution model.

The codec round-trips all current keyboard, mouse, control-flow, Session cancellation and Event emission steps. Conditions compile context references once and evaluate their values only when the executor reaches them.

## YAML IR example

An executor Component can consume the IR directly from `spec.options.steps`:

```yaml
steps:
  - action: if
    condition: {ref: session#macroEnabled, operator: EQUALS, value: true}
    then:
      - action: keyTap
        key: {code: A, location: STANDARD}
        holdMillis: 50
      - action: sleep
        durationMillis: 100
      - action: emit
        eventType: macro.completed
        copyInputData: true
        data: {macro: {status: completed}}
    else:
      - action: emit
        eventType: macro.skipped
        data: {macro: {status: disabled}}
```

The same `MacroProgramDefinition` can be produced through `MacroBuilder`, the Kotlin frontend, or a future frontend. `macro-spec` does not know which device driver eventually executes keyboard and mouse steps.

## Layer boundary

- `user-interaction-spec` owns platform-neutral keyboard/mouse value types.
- `macro-spec` owns immutable steps, conditions, limits, codecs and frontend registration.
- An authoring frontend translates source text into `MacroProgramDefinition`.
- An executor interprets that definition and delegates physical operations to a driver.

The first three boundaries are already independent plugins. The current AWT implementation still contains both the generic step interpreter and the AWT driver; extracting the interpreter into a reusable executor layer remains the next decoupling step.
