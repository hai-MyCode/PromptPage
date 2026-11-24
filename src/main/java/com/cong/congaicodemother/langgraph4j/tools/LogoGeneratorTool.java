package com.cong.congaicodemother.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import com.cong.congaicodemother.langgraph4j.model.ImageResource;
import com.cong.congaicodemother.langgraph4j.model.enums.ImageCategoryEnum;
import com.cong.congaicodemother.manager.DashScopeImageClient;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Logo 图片生成工具
 * <p>
 * 使用 DashScope 的 qwen-image 系列模型，按配置顺序尝试，
 * 前一个模型额度用完或调用失败时自动切换下一个。
 */
@Slf4j
@Component
public class LogoGeneratorTool {

    @Resource
    private DashScopeImageClient dashScopeImageClient;

    /**
     * 逗号分隔的模型列表，按顺序降级
     */
    @Value("${dashscope.image-models:qwen-image-3.0-pro,qwen-image-3.0}")
    private String imageModels;

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        // Logo 中禁止出现文字，避免模型乱造字母
        String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);

        List<String> models = StrUtil.split(imageModels, ',');
        for (String item : models) {
            String model = StrUtil.trim(item);
            if (StrUtil.isBlank(model)) {
                continue;
            }
            try {
                String imageUrl = dashScopeImageClient.generate(model, logoPrompt);
                if (StrUtil.isBlank(imageUrl)) {
                    continue;
                }
                logoList.add(ImageResource.builder()
                        .category(ImageCategoryEnum.LOGO)
                        .description(description)
                        .url(imageUrl)
                        .build());
                log.info("Logo 生成成功，使用模型：{}", model);
                return logoList;
            } catch (Exception e) {
                // 额度不足、模型不可用等，继续尝试下一个模型
                log.warn("模型 {} 生成 Logo 失败，尝试下一个：{}", model, e.getMessage());
            }
        }
        log.error("所有图片模型均生成失败，已配置模型：{}", imageModels);
        return logoList;
    }
}
