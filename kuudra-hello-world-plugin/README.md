# Kuudra Hello World Plugin

`HelloWorldEventSource` 按 `intervalMillis` 周期产生 `hello-world.tick` 事件。事件数据位于
`hello-world` 命名空间，`message` 属性固定为字符串 `hello-world`。EventSource 只产生
`KuudraEvent`；Kuudra Runtime 在路由入口负责附加无会话执行域，插件不依赖内部 Wrapper API。
插件属于官方 `kuudra-official` 插件命名空间。

```yaml
apiVersion: kuudra.io/v1alpha1
kind: EventSource
metadata:
  namespace: demo
  name: hello-world-source
spec:
  component: kuudra-official/hello-world/hello-world
  desiredState: running
  options:
    intervalMillis: 1000
```

构建前先在 Kuudra 内核仓库执行：

```powershell
mvn -pl kuudra-api,kuudra-plugin -am install -DskipTests
```

然后在 demos 聚合工程执行：

```powershell
mvn clean package
```
