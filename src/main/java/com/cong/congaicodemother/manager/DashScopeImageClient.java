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

/**
 * 阿里云百炼（DashScope）图片生成客户端
 * <p>
 * 走 OpenAI 兼容协议：POST {base-url}/images/generations
 * 支持 qwen-image-3.0 / qwen-image-3.0-pro 等新模型。
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
        String requestBody = JSONUtil.createObj()
                .set("model", model)
                .set("prompt", prompt)
                .set("n", 1)
                .set("size", imageSize)
                .toString();

        log.info("调用图片生成：model={}, size={}", model, imageSize);
        try (HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .timeout(180_000)
                .execute()) {
            String responseBody = response.body();
            if (!response.isOk()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "图片生成失败，HTTP " + response.getStatus() + "：" + abbreviate(responseBody));
            }
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
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片生成调用异常：" + e.getMessage());
        }
    }

    private String abbreviate(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
