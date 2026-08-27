# Kuudra Default Plugin

`kuudra-official/default` supplies reusable boundary and event-processing components for Kuudra v0.4.0.

| Reference | Domain | Purpose |
| --- | --- | --- |
| `ingress/kuudra-official/plain-ingress` | RAW → SESSION | Admit every event and select its session group |
| `egress/kuudra-official/plain-egress` | SESSION → RAW | Remove session execution state |
| `event-adapter/kuudra-official/event-mapper` | RAW or SESSION | Retype an event and project namespaced data |
| `event-adapter/kuudra-official/event-filter` | RAW or SESSION | Apply declarative ALL/ANY filtering rules |
| `event-interpreter/kuudra-official/sequential-event` | RAW | Recognize an ordered event sequence in a time window |
| `event-interpreter/kuudra-official/any-order-event` | RAW | Recognize required events in any order in a time window |
| `event-handler/kuudra-official/system-control` | SESSION | Route events to kernel/session control requests |

The mapper covers the historical Union trigger: set `outputType` and/or `data` to map a lower-level event to an upper-level event. Mapper values support Kuudra placeholders because Runtime resolves component options before invocation.

Interpreter selectors are objects whose keys are either `type` or namespaced data paths such as `keyboard.key.code`. A requirement contains a selector and a positive `count`. `forbidden` selectors reset current progress. The timeout starts at the first relevant event; unrelated events do not reset progress. Successful output uses the configured `outputType` and places `interpreter`, `matchCount`, and optionally `matchedEvents` under the `kuudra-official` data namespace.

Filter operators are `EXISTS`, `NOT_EXISTS`, `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, numeric/string comparison operators, `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, and `MATCHES_REGEX`.

See [`examples/advanced-event-pipeline.yaml`](examples/advanced-event-pipeline.yaml) for a complete multi-document resource manifest.
