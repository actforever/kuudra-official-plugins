# Kuudra Session Probe Plugin

`kuudra-official/session-probe` 是可部署的会话调度诊断插件，不属于业务日志插件：

- `event-source/kuudra-official/session-probe-source` 产生数量有限、时序确定的 Event；
- `event-handler/kuudra-official/session-probe-handler` 持有 Session 租约，并通过 `ExecutionControl.checkpoint()` 观察暂停与依赖终止传播。

它用于复验 SERIAL/PARALLEL 调度、Session 依赖图和协作式取消。完整双 Flow 示例位于 `examples/session-dependency/manifests.yaml`。
