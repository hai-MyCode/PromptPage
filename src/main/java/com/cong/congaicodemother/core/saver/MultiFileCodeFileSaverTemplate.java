package com.cong.congaicodemother.core.saver;

import cn.hutool.core.util.StrUtil;
import com.cong.congaicodemother.ai.model.MultiFileCodeResult;
import com.cong.congaicodemother.exception.BusinessException;
import com.cong.congaicodemother.exception.ErrorCode;
import com.cong.congaicodemother.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;

/**
 * 多文件代码保存器
 *
 */
@Slf4j
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        // 保存 HTML 文件
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());

        // CSS / JS 为空时写入占位文件：避免 index.html 里的 <link>/<script> 变成 404
        String cssCode = result.getCssCode();
        String jsCode = result.getJsCode();
        if (StrUtil.isBlank(cssCode)) {
            log.warn("CSS 内容为空，写入占位文件；页面可能没有样式（通常是模型输出被截断）");
            cssCode = "/* style.css 未生成 */";
        } else if (!cssCode.trim().endsWith("}") && !cssCode.trim().endsWith("*/")) {
            log.warn("CSS 内容可能被截断（未以 }} 结尾），已尽量保存已生成部分");
        }
        if (StrUtil.isBlank(jsCode)) {
            log.warn("JS 内容为空，写入占位文件");
            jsCode = "// script.js 未生成";
        }
        writeToFile(baseDirPath, "style.css", cssCode);
        writeToFile(baseDirPath, "script.js", jsCode);
    }

    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        // 至少要有 HTML 代码，CSS 和 JS 可以为空
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
    }
}
