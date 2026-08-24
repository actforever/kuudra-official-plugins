# Kuudra Logging Plugin

提供 `event-handler/kuudra-official/event-logger`，把收到的事件写入 Kuudra 内核日志。

```yaml
apiVersion: kuudra.io/v1alpha1
kind: EventHandler
metadata:
  namespace: default
  name: logger
spec:
  component: kuudra-official/event-logger
  options:
    level: INFO
    message: "received event"
    includeData: true
```

`level` 支持 `TRACE`、`DEBUG`、`INFO`、`WARN`、`ERROR`。日志自动携带
`namespace=kuudra-official` 与 `pluginId=logging`，插件不依赖 SLF4J 或 Logback。
