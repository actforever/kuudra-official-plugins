# Kuudra Macro Spec Plugin

`actforever/macro-spec` defines the language-neutral immutable macro IR, its YAML codec, builder API, conditions and frontend registry. It contains no Flow component and performs no device I/O. Authoring frontends produce `MacroProgramDefinition`; executor plugins consume it, so YAML, Kotlin and future languages share one execution model.

The codec round-trips all current keyboard, mouse, control-flow, Session cancellation and Event emission steps. Conditions compile context references once and evaluate their values only when the executor reaches them.
