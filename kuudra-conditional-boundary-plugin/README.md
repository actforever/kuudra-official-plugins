# Kuudra Conditional Boundary Plugin

`kuudra-official/conditional-boundary` provides explicit conditional domain boundaries:

- `ingress/kuudra-official/conditional-ingress` admits RAW Events only when its condition matches. It can also declare active Session dependencies.
- `egress/kuudra-official/conditional-egress` exports SESSION Events only when its condition matches.

Runtime resolves placeholders before invocation. Conditional Ingress can read Event, Flow, and Global scopes; Conditional Egress can additionally read Session scope.

```yaml
apiVersion: kuudra.io/v1alpha1
kind: Ingress
metadata:
  namespace: dev
  name: guarded-entry
spec:
  component: ingress/kuudra-official/conditional-ingress
  desiredState: active
  options:
    condition: "${global#automation-enabled}"
    operator: EQUALS
    value: true
    groupKey: "${event#kuudra-official.device-id}"
    policy: SERIAL
    dependencies:
      - selector:
          flowId: dev/window-flow
          ingressComponentId: ingress/dev/window-entry
          groupKey: "${event#kuudra-official.window-id}"
          matchPolicy: UNIQUE
        terminationPolicy: CANCEL_DEPENDENT
```

The scheduling policy controls competing Events in this Ingress group. Dependencies are resolved only when an admission actually starts, after SERIAL queues or replacement policies have run. A missing or ambiguous dependency rejects Session dispatch.

```yaml
apiVersion: kuudra.io/v1alpha1
kind: Egress
metadata:
  namespace: dev
  name: completed-only
spec:
  component: egress/kuudra-official/conditional-egress
  desiredState: active
  options:
    condition: "${session#completed}"
    operator: EQUALS
    value: true
```
