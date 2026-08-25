package io.github.actforever.kuudra.demo.logging;

import io.github.actforever.kuudra.api.ActionContext;
import io.github.actforever.kuudra.api.EventHandler;
import io.github.actforever.kuudra.api.KuudraEvent;
import io.github.actforever.kuudra.plugin.PluginComponentContext;
import io.github.actforever.kuudra.plugin.PluginComponentLifecycle;
import io.github.actforever.kuudra.plugin.PluginLogLevel;
import io.github.actforever.kuudra.plugin.PluginLogger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@io.github.actforever.kuudra.plugin.annotation.EventHandler("event-logger")
@io.github.actforever.kuudra.plugin.annotation.ComponentDoc(
        purpose = "将收到的 KuudraEvent 通过绑定插件身份的内核 Logger 输出。",
        usageExample = "level: INFO\nmessage: 'received event'\nincludeData: true",
        lifecyclePhases = {"initialize: 获取插件 Logger", "handle: 按配置级别记录事件"},
        configuration = {
                @io.github.actforever.kuudra.plugin.annotation.SpecProperty(
                        path = "level", type = PluginLogLevel.class, defaultValue = "INFO",
                        description = "插件日志级别。", examples = {"\"INFO\"", "\"DEBUG\""},
                        allowedValues = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"}),
                @io.github.actforever.kuudra.plugin.annotation.SpecProperty(
                        path = "message", type = String.class,
                        description = "日志正文；未配置时使用 received event <event-type>。",
                        examples = {"\"received hello-world event\"", "\"event accepted\""}),
                @io.github.actforever.kuudra.plugin.annotation.SpecProperty(
                        path = "includeData", type = Boolean.class, defaultValue = "true",
                        description = "是否把完整 EventData 命名空间树附加到日志字段。",
                        examples = {"true", "false"})
        })
public final class LoggingEventHandler implements EventHandler, PluginComponentLifecycle {
    private PluginLogger logger;

    @Override
    public CompletionStage<Void> initialize(PluginComponentContext context) {
        logger = context.logger();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> handle(KuudraEvent event, ActionContext context) {
        PluginLogLevel level = context.configuration("level", PluginLogLevel.class, PluginLogLevel.INFO);
        String message = context.configuration("message", String.class, "received event " + event.type());
        boolean includeData = context.configuration("includeData", Boolean.class, true);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("eventId", event.id().toString());
        fields.put("eventType", event.type());
        fields.put("flowId", context.flowId());
        fields.put("sessionId", context.sessionId().toString());
        if (includeData) fields.put("data", event.data().namespaces());
        logger.log(level, message, fields, null);
        return CompletableFuture.completedFuture(null);
    }
}
