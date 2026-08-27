# Kuudra Conditional Boundary Plugin

`kuudra-official/conditional-boundary` provides explicit conditional domain boundaries:

- `ingress/kuudra-official/conditional-ingress` admits RAW Events only when its condition matches, then assigns a group key and Session labels.
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
    sessionLabels:
      role: job
```

Ingress does not reference or parse scheduling/dependency configuration. Runtime automatically selects at most one same-namespace `SessionCoordinationPolicy` from the produced labels, and dependency matching is restricted to the current Flow.

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
