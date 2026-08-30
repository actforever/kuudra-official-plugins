package io.github.actforever.kuudra.demo.logging;

import io.github.actforever.kuudra.api.event.EventHandlerContext;
import io.github.actforever.kuudra.api.event.KuudraEvent;
import io.github.actforever.kuudra.plugin.ResourceContext;
import io.github.actforever.kuudra.plugin.ResourceLifecycle;
import io.github.actforever.kuudra.plugin.PluginLogLevel;
import io.github.actforever.kuudra.plugin.PluginLogger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@io.github.actforever.kuudra.plugin.annotation.Controller("event-logger")
@io.github.actforever.kuudra.plugin.annotation.ResourceDoc(
        purpose = "将收到的 KuudraEvent 通过绑定插件身份的内核 Logger 输出。",
        lifecyclePhases = {"initialize: 获取插件 Logger", "handle: 按配置级别记录事件"},
        arguments = {
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
public final class LoggingEventHandler implements ResourceLifecycle {
    private PluginLogger logger;

    @Override
    public CompletionStage<Void> initialize(ResourceContext context) {
        logger = context.logger();
        return CompletableFuture.completedFuture(null);
    }

    @io.github.actforever.kuudra.plugin.annotation.EventHandler("log")
    public CompletionStage<Void> handle(KuudraEvent event, EventHandlerContext context) {
        PluginLogLevel level = context.arguments().getOrDefault("level", PluginLogLevel.class, PluginLogLevel.INFO);
        String message = context.arguments().getOrDefault("message", String.class, "received event " + event.type());
        boolean includeData = context.arguments().getOrDefault("includeData", Boolean.class, true);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("eventId", event.id().toString());
        fields.put("eventType", event.type());
        fields.put("abilityId", context.abilityId());
        fields.put("sessionId", context.sessionId().toString());
        if (includeData) fields.put("data", event.data().namespaces());
        logger.log(level, message, fields, null);
        return CompletableFuture.completedFuture(null);
    }
}
