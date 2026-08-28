# Safe YAML macro example

Deploy the `hello-world`, `default`, `logging`, `user-interaction-spec`, `macro-spec`, and `awt-robot` plugin JARs, then copy `manifests.yaml` into `<home>/manifests`.

The macro exercises YAML decoding, context conditions, Session-domain execution and downstream Event routing without injecting physical keyboard or mouse input. A healthy run repeatedly logs:

```text
Macro result: yaml-executed
```
