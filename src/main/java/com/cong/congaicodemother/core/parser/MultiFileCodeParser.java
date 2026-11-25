package com.cong.congaicodemother.core.parser;

import com.cong.congaicodemother.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多文件代码解析器（HTML + CSS + JS）
 * <p>
 * 兼容模型输出被截断的情况：最后一个代码块可能没有结束的 ```，
 * 此时把标记之后的内容全部当作该文件内容，避免"整段样式丢失"。
 *
 */
public class MultiFileCodeParser implements CodeParser<MultiFileCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    @Override
    public MultiFileCodeResult parseCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        // 提取各类代码（正常闭合的代码块）
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        String cssCode = extractCodeByPattern(codeContent, CSS_CODE_PATTERN);
        String jsCode = extractCodeByPattern(codeContent, JS_CODE_PATTERN);
        // 兼容截断：没有闭合 ``` 时，取标记之后的全部内容
        if (htmlCode == null) {
            htmlCode = extractUnclosedBlock(codeContent, "html");
        }
        if (cssCode == null) {
            cssCode = extractUnclosedBlock(codeContent, "css");
        }
        if (jsCode == null) {
            jsCode = extractUnclosedBlock(codeContent, "js|javascript");
        }
        // 设置HTML代码
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }
        // 设置CSS代码
        if (cssCode != null && !cssCode.trim().isEmpty()) {
            result.setCssCode(cssCode.trim());
        }
        // 设置JS代码
        if (jsCode != null && !jsCode.trim().isEmpty()) {
            result.setJsCode(jsCode.trim());
        }
        return result;
    }

    /**
     * 根据正则模式提取代码
     *
     * @param content 原始内容
     * @param pattern 正则模式
     * @return 提取的代码
     */
    private String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 提取没有闭合围栏的代码块（通常发生在模型输出被 max-tokens 截断时）
     *
     * @param content 原始内容
     * @param lang    代码块语言标记，如 html / css / js|javascript
     * @return 标记之后剩余的全部内容，找不到标记返回 null
     */
    private String extractUnclosedBlock(String content, String lang) {
        Matcher matcher = Pattern.compile("```(?:" + lang + ")\\s*\\n", Pattern.CASE_INSENSITIVE).matcher(content);
        if (!matcher.find()) {
            return null;
        }
        int start = matcher.end();
        int end = content.indexOf("```", start);
        return end > 0 ? content.substring(start, end) : content.substring(start);
    }
}
