package com.cong.congaicodemother.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 构建 Vue 项目
 */
@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 构建结果
     *
     * @param success 是否成功
     * @param output  命令输出（失败时用于自动修复）
     */
    public record BuildResult(boolean success, String output) {
    }

    /**
     * 异步构建 Vue 项目
     *
     * @param projectPath
     */
    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    try {
                        buildProject(projectPath);
                    } catch (Exception e) {
                        log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
                    }
                });
    }

    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        return buildProjectWithResult(projectPath).success();
    }

    /**
     * 构建 Vue 项目，并返回命令输出（失败时可用于让 AI 自动修复）
     *
     * @param projectPath 项目根目录路径
     * @return 构建结果
     */
    public BuildResult buildProjectWithResult(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在：{}", projectPath);
            return new BuildResult(false, "项目目录不存在：" + projectPath);
        }
        // 检查是否有 package.json 文件
        File packageJsonFile = new File(projectDir, "package.json");
        if (!packageJsonFile.exists()) {
            log.error("项目目录中没有 package.json 文件：{}", projectPath);
            return new BuildResult(false, "项目目录中没有 package.json 文件：" + projectPath);
        }
        log.info("开始构建 Vue 项目：{}", projectPath);
        StringBuilder output = new StringBuilder();
        // 执行 npm install
        if (!executeNpmInstall(projectDir, output)) {
            log.error("npm install 执行失败：{}\n{}", projectPath, output);
            return new BuildResult(false, "npm install 执行失败：\n" + output);
        }
        // 执行 npm run build
        if (!executeNpmBuild(projectDir, output)) {
            log.error("npm run build 执行失败：{}\n{}", projectPath, output);
            return new BuildResult(false, "npm run build 执行失败：\n" + output);
        }
        // 验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists() || !distDir.isDirectory()) {
            log.error("构建完成但 dist 目录未生成：{}", projectPath);
            return new BuildResult(false, "构建完成但 dist 目录未生成");
        }
        log.info("Vue 项目构建成功，dist 目录：{}", projectPath);
        return new BuildResult(true, output.toString());
    }

    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir, StringBuilder output) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300, output); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir, StringBuilder output) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180, output); // 3分钟超时
    }

    /**
     * 根据操作系统构造命令
     *
     * @param baseCommand
     * @return
     */
    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }

    /**
     * 操作系统检测
     *
     * @return
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 执行命令，并把标准输出/错误输出收集到 output 中
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @param output         命令输出收集器
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds, StringBuilder output) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            // 记录本次命令的起始位置，失败时只返回本条命令的输出
            int startIndex = output.length();
            ProcessBuilder processBuilder = new ProcessBuilder(command.split("\\s+"));
            processBuilder.directory(workingDir);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            // 单独线程读取输出，避免缓冲区写满导致进程阻塞
            Thread reader = Thread.ofVirtual().start(() -> {
                try (BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        synchronized (output) {
                            output.append(line).append('\n');
                        }
                    }
                } catch (IOException e) {
                    log.warn("读取命令输出失败：{}", e.getMessage());
                }
            });
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                reader.join(2000);
                return false;
            }
            reader.join(2000);
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            }
            log.error("命令执行失败，退出码: {}，输出：\n{}", exitCode, output.substring(startIndex));
            return false;
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return false;
        }
    }

}
