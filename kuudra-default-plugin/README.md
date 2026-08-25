# Kuudra Default Plugin

Kuudra 官方基础组件插件，身份为 `kuudra-official/default`。构建后的 JAR 应部署到 `<home-directory>/plugins`。

组件：

- `ingress/kuudra-official/default-ingress`：无条件放行任意 Event，并根据配置计算会话组。
- `egress/kuudra-official/default`：将 Session Event 导出回 RAW 域。
- `event-handler/kuudra-official/system-control`：提交内核或 Session 控制请求。

Flow 不是插件组件，而是内核拥有的声明式路由资源。插件提供可被 Flow 导入的节点实现，App 负责解析和校验 `imports/edges`，Runtime 负责把它编译成调度图。

```powershell
mvn -pl kuudra-api,kuudra-plugin -am install -DskipTests
mvn package
```
