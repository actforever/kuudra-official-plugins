# Session dependency verification

将 `kuudra-conditional-boundary-plugin` 与 `kuudra-session-probe-plugin` JAR 放入 `.kuudra/plugins`，并把 `manifests.yaml` 放入 `.kuudra/manifests`。

同一个 Flow 的窗口分支先创建带有 `role=window` 标签的 Session；作业分支随后产生两个事件，并由 `jobs-in-window` 策略按 `role=job` 自动选中、以 SERIAL 方式依赖窗口。窗口结束时，第一个作业应收到协作式取消；第二个作业出队时重新解析同 Flow 内的标签依赖，因为窗口已经结束而被拒绝，不会到达 Handler。Ingress 不持有策略引用。

可通过 `GET /api/v1/runtime/sessions/dependencies` 在窗口存活期间观察活动依赖边，并在日志中观察 `session.dependency.established`、`session.dependency.termination-propagated` 与 `session.dependency.rejected`。
