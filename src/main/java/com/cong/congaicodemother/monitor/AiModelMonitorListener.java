package com.cong.congaicodemother.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * AI 模型监听器
 * <p>
 * 注意：流式调用时 onResponse / onError 可能在不同线程触发，
 * ThreadLocal 里的 MonitorContext 可能为空，所有取值都必须做空值保护。
 */
@Slf4j
@Component
public class AiModelMonitorListener implements ChatModelListener {

    // 用于存储请求开始时间的键
    private static final String REQUEST_START_TIME_KEY = "request_start_time";
    // 用于监控上下文传递（因为请求和响应事件的触发不是同一个线程）
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";

    private static final String UNKNOWN = "unknown";

    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(REQUEST_START_TIME_KEY, Instant.now());
        MonitorContext context = MonitorContextHolder.getContext();
        if (context == null) {
            context = MonitorContext.builder().userId(UNKNOWN).appId(UNKNOWN).build();
        }
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, context);
        String modelName = requestContext.chatRequest().modelName();
        aiModelMetricsCollector.recordRequest(context.getUserId(), context.getAppId(), modelName, "started");
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Map<Object, Object> attributes = responseContext.attributes();
        MonitorContext context = resolveContext(attributes);
        String modelName = responseContext.chatResponse().modelName();
        aiModelMetricsCollector.recordRequest(context.getUserId(), context.getAppId(), modelName, "success");
        recordResponseTime(attributes, context.getUserId(), context.getAppId(), modelName);
        recordTokenUsage(responseContext, context.getUserId(), context.getAppId(), modelName);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        String modelName = errorContext.chatRequest() == null
                ? UNKNOWN
                : errorContext.chatRequest().modelName();
        Throwable error = errorContext.error();
        String errorType = error == null ? UNKNOWN : error.getClass().getSimpleName();
        String errorMessage = error == null ? UNKNOWN : String.valueOf(error.getMessage());
        if (errorMessage.length() > 2000) {
            errorMessage = errorMessage.substring(0, 2000) + "...";
        }
        // 先记录日志，避免被后续异常吞掉
        log.error("AI 模型调用失败：model={}, type={}, error={}", modelName, errorType, errorMessage);

        Map<Object, Object> attributes = errorContext.attributes();
        MonitorContext context = resolveContext(attributes);
        aiModelMetricsCollector.recordRequest(context.getUserId(), context.getAppId(), modelName, "error");
        aiModelMetricsCollector.recordError(context.getUserId(), context.getAppId(), modelName, errorMessage);
        recordResponseTime(attributes, context.getUserId(), context.getAppId(), modelName);
    }

    /**
     * 优先从 attributes 取上下文，其次取 ThreadLocal，最后返回 unknown
     */
    private MonitorContext resolveContext(Map<Object, Object> attributes) {
        if (attributes != null) {
            Object value = attributes.get(MONITOR_CONTEXT_KEY);
            if (value instanceof MonitorContext context) {
                return context;
            }
        }
        MonitorContext context = MonitorContextHolder.getContext();
        if (context != null) {
            return context;
        }
        return MonitorContext.builder().userId(UNKNOWN).appId(UNKNOWN).build();
    }

    /**
     * 记录响应时间
     */
    private void recordResponseTime(Map<Object, Object> attributes, String userId, String appId, String modelName) {
        Object start = attributes == null ? null : attributes.get(REQUEST_START_TIME_KEY);
        if (!(start instanceof Instant startTime)) {
            return;
        }
        Duration responseTime = Duration.between(startTime, Instant.now());
        aiModelMetricsCollector.recordResponseTime(userId, appId, modelName, responseTime);
    }

    /**
     * 记录Token使用情况
     */
    private void recordTokenUsage(ChatModelResponseContext responseContext, String userId, String appId, String modelName) {
        TokenUsage tokenUsage = responseContext.chatResponse().metadata() == null
                ? null
                : responseContext.chatResponse().metadata().tokenUsage();
        if (tokenUsage != null) {
            log.info("AI Token 用量：model={}, input={}, output={}, total={}",
                    modelName, tokenUsage.inputTokenCount(), tokenUsage.outputTokenCount(), tokenUsage.totalTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "input", tokenUsage.inputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "output", tokenUsage.outputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "total", tokenUsage.totalTokenCount());
        }
    }
}
