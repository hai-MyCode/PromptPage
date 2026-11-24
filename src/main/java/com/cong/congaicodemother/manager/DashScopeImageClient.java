package com.cong.congaicodemother.manager;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.cong.congaicodemother.exception.BusinessException;
import com.cong.congaicodemother.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云百炼（DashScope）图片生成客户端
 * <p>
 * 走 OpenAI 兼容协议：POST {base-url}/images/generations
 * 支持 qwen-image-3.0 / qwen-image-3.0-pro 等模型。
 * <p>
 * 内置两个保护：
 * 1. 串行调用（同一时刻只发一个请求），降低触发 QPS 限流的概率；
 * 2. 遇到 429 / 5xx / 网络异常时按退避策略重试。
 */
@Slf4j
@Component
public class DashScopeImageClient {

    @Value("${dashscope.api-key:}")
    private String apiKey;

    @Value("${dashscope.image-base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${dashscope.image-size:1024*1024}")
    private String imageSize;

    /**
     * 单次生成的最大尝试次数（含首次）
     */
    @Value("${dashscope.image-max-attempts:3}")
    private int maxAttempts;

    /**
     * 重试基础间隔，第 n 次重试等待 delay * n
     */
    @Value("${dashscope.image-retry-delay-ms:3000}")
    private long retryDelayMs;

    /**
     * 串行化：图片生成比较慢，并发调用很容易触发限流
     */
    private final Semaphore concurrency = new Semaphore(1);

    /**
     * 生成一张图片，返回图片 URL（或 base64 data URI）
     *
     * @param model  模型名称，如 qwen-image-3.0-pro
     * @param prompt 提示词
     * @return 图片地址
     */
    public String generate(String model, String prompt) {
        if (StrUtil.isBlank(apiKey) || apiKey.startsWith("<")) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "未配置 dashscope.api-key，请在 application-local.yml 中填写");
        }
        String url = StrUtil.removeSuffix(baseUrl, "/") + "/images/generations";

        BusinessException lastError = null;
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return requestOnce(url, model, prompt);
            } catch (BusinessException e) {
                lastError = e;
                if (!isRetryable(e) || attempt >= attempts) {
                    throw e;
                }
                long delay = retryDelayMs * attempt;
                log.warn("图片生成触发限流/临时错误，{}ms 后重试（第 {}/{} 次）：{}",
                        delay, attempt, attempts, e.getMessage());
                sleep(delay);
            }
        }
        throw lastError == null
                ? new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成失败")
                : lastError;
    }

    private String requestOnce(String url, String model, String prompt) {
        String requestBody = JSONUtil.createObj()
                .set("model", model)
                .set("prompt", prompt)
                .set("n", 1)
                .set("size", imageSize)
                .toString();

        boolean acquired = false;
        try {
            acquired = concurrency.tryAcquire(5, TimeUnit.MINUTES);
            if (!acquired) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成排队超时，请稍后重试");
            }
            log.info("调用图片生成：model={}, size={}", model, imageSize);
            try (HttpResponse response = HttpRequest.post(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .timeout(180_000)
                    .execute()) {
                String responseBody = response.body();
                int status = response.getStatus();
                if (status == 429) {
                    throw new BusinessException(ErrorCode.TOO_MANY_REQUEST,
                            "HTTP 429 图片生成触发限流：" + abbreviate(responseBody));
                }
                if (status >= 500) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                            "HTTP " + status + " 图片服务异常：" + abbreviate(responseBody));
                }
                if (!response.isOk()) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                            "图片生成失败，HTTP " + status + "：" + abbreviate(responseBody));
                }
                return parseImageUrl(responseBody);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成被中断");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成调用异常：" + e.getMessage());
        } finally {
            if (acquired) {
                concurrency.release();
            }
        }
    }

    private String parseImageUrl(String responseBody) {
        JSONObject json = JSONUtil.parseObj(responseBody);
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "图片生成返回为空：" + abbreviate(responseBody));
        }
        JSONObject first = data.getJSONObject(0);
        String imageUrl = first.getStr("url");
        if (StrUtil.isNotBlank(imageUrl)) {
            return imageUrl;
        }
        // 兼容只返回 base64 的情况
        String base64 = first.getStr("b64_json");
        if (StrUtil.isNotBlank(base64)) {
            return "data:image/png;base64," + base64;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                "图片生成返回中没有 url：" + abbreviate(responseBody));
    }

    /**
     * 只有限流、服务端异常、网络超时值得重试；参数错误、鉴权失败重试也没用
     */
    private boolean isRetryable(BusinessException e) {
        String message = StrUtil.nullToEmpty(e.getMessage());
        return message.contains("HTTP 429")
                || message.contains("limit_requests")
                || message.contains("HTTP 5")
                || message.contains("超时")
                || message.contains("timeout")
                || message.contains("timed out");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成重试被中断");
        }
    }

    private String abbreviate(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
