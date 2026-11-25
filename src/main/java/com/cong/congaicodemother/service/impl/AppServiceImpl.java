package com.cong.congaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.cong.congaicodemother.ai.AiCodeGenTypeRoutingService;
import com.cong.congaicodemother.ai.AiCodeGenTypeRoutingServiceFactory;
import com.cong.congaicodemother.constant.AppConstant;
import com.cong.congaicodemother.constant.UserConstant;
import com.cong.congaicodemother.core.AiCodeGeneratorFacade;
import com.cong.congaicodemother.core.builder.VueProjectBuilder;
import com.cong.congaicodemother.core.handler.StreamHandlerExecutor;
import com.cong.congaicodemother.exception.BusinessException;
import com.cong.congaicodemother.exception.ErrorCode;
import com.cong.congaicodemother.exception.ThrowUtils;
import com.cong.congaicodemother.model.dto.app.AppAddRequest;
import com.cong.congaicodemother.model.dto.app.AppQueryRequest;
import com.cong.congaicodemother.model.entity.App;
import com.cong.congaicodemother.mapper.AppMapper;
import com.cong.congaicodemother.model.entity.User;
import com.cong.congaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.cong.congaicodemother.model.enums.CodeGenTypeEnum;
import com.cong.congaicodemother.model.vo.AppVO;
import com.cong.congaicodemother.model.vo.UserVO;
import com.cong.congaicodemother.monitor.MonitorContext;
import com.cong.congaicodemother.monitor.MonitorContextHolder;
import com.cong.congaicodemother.service.AppService;
import com.cong.congaicodemother.service.ChatHistoryService;
import com.cong.congaicodemother.service.ScreenshotService;
import com.cong.congaicodemother.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    /** 正在生成中的应用，防止前端重连/重复点击导致重复调用模型 */
    private final Set<Long> generatingApps = ConcurrentHashMap.newKeySet();

    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;

    /**
     * 路由模型最大尝试次数（含首次）
     */
    @Value("${app.routing.max-attempts:3}")
    private int routingMaxAttempts;

    /**
     * 路由重试基础间隔（毫秒），第 n 次等待 delay * n
     */
    @Value("${app.routing.retry-delay-ms:2000}")
    private long routingRetryDelayMs;

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验，仅本人可以和自己的应用对话
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }
        // 4.5 如果用户只是询问进度/结果，不调用 AI、不写文件、不扣额度，直接返回当前状态
        if (isStatusQuestion(message)) {
            String answer = buildStatusAnswer(appId, codeGenTypeEnum);
            chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
            chatHistoryService.addChatMessage(appId, answer, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
            return Flux.just(answer);
        }
        // 5. 同一应用同一时间只允许一个生成任务，防止前端 EventSource 自动重连导致重复调用、重复消耗 token
        boolean acquired = generatingApps.add(appId);
        ThrowUtils.throwIf(!acquired, ErrorCode.OPERATION_ERROR, "该应用正在生成中，请稍候再试");
        // 6. 扣减额度（每个账号的生成次数，原子扣减，额度不足直接拦截）
        boolean deducted = userService.tryDeductQuota(loginUser.getId());
        if (!deducted) {
            generatingApps.remove(appId);
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "本账号的生成额度已用完，请联系管理员充值");
        }
        // 7. 在调用 AI 前，先保存用户消息到数据库中
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 8. 设置监控上下文（用户 ID 和应用 ID）
        MonitorContextHolder.setContext(
                MonitorContext.builder()
                        .userId(loginUser.getId().toString())
                        .appId(appId.toString())
                        .build()
        );
        // 9. 调用 AI 生成代码（流式）
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        // 10. 收集 AI 响应的内容，并且在完成后保存记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                // 兜底超时：模型长时间无响应时主动结束，避免前端一直转圈
                .timeout(Duration.ofMinutes(15))
                .doOnError(error -> {
                    // 生成失败时把额度退回，避免用户白扣
                    userService.refundQuota(loginUser.getId());
                })
                .doFinally(signalType -> {
                    // 流结束时清理（无论成功/失败/取消）
                    generatingApps.remove(appId);
                    MonitorContextHolder.clearContext();
                });
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型（千问）
        // 路由只是辅助能力：限流/不可用时重试几次，仍失败则降级为本地规则，不能阻塞创建应用
        CodeGenTypeEnum selectedCodeGenType;
        try {
            selectedCodeGenType = routeCodeGenTypeWithRetry(initPrompt);
        } catch (Exception e) {
            log.warn("AI 路由失败，使用兜底规则：{}", e.getMessage());
            selectedCodeGenType = fallbackCodeGenType(initPrompt);
        }
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    /**
     * 路由模型不可用时的兜底规则：按需求描述长度粗略判断生成类型
     */
    private CodeGenTypeEnum fallbackCodeGenType(String initPrompt) {
        int length = initPrompt == null ? 0 : initPrompt.length();
        if (length > 120) {
            return CodeGenTypeEnum.VUE_PROJECT;
        }
        if (length > 40) {
            return CodeGenTypeEnum.MULTI_FILE;
        }
        return CodeGenTypeEnum.HTML;
    }

    /**
     * 调用千问做代码生成类型路由，遇到 429 限流时按 2s、4s 退避重试
     */
    private CodeGenTypeEnum routeCodeGenTypeWithRetry(String initPrompt) {
        AiCodeGenTypeRoutingService routingService =
                aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        int attempts = Math.max(1, routingMaxAttempts);
        Exception lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                CodeGenTypeEnum type = routingService.routeCodeGenType(initPrompt);
                if (type != null) {
                    return type;
                }
                log.warn("千问路由返回为空（第 {}/{} 次）", attempt, attempts);
            } catch (Exception e) {
                lastError = e;
                log.warn("千问路由调用失败（第 {}/{} 次）：{}", attempt, attempts, e.getMessage());
                if (attempt < attempts) {
                    sleepQuietly(routingRetryDelayMs * attempt);
                }
            }
        }
        throw lastError == null
                ? new BusinessException(ErrorCode.OPERATION_ERROR, "路由结果为空")
                : new BusinessException(ErrorCode.OPERATION_ERROR, "路由失败：" + lastError.getMessage());
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验，仅本人可以部署自己的应用
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 如果没有，则生成 6 位 deployKey（字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，获取原始代码生成路径（应用访问目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查路径是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在，请先生成应用");
        }
        // 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请重试");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 构建完成后，需要将构建后的文件复制到部署目录
            sourceDir = distDir;
        }
        // 8. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败：" + e.getMessage());
        }
        // 9. 更新数据库
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10. 构建应用访问 URL
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);        // 11. 异步生成截图并且更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    /**
     * 从构建输出中提取关键错误信息，并压缩到指定长度以内
     * （输入护轨限制单条消息不能超过 1000 字）
     */
    private String compactBuildError(String output, int maxLength) {
        if (StrUtil.isBlank(output)) {
            return "构建失败，但未获取到具体错误，请检查 package.json 和源码。";
        }
        StringBuilder keyLines = new StringBuilder();
        for (String line : output.split("\\R")) {
            String text = line.trim();
            if (text.isEmpty() || text.startsWith("at ") || text.startsWith("...")) {
                continue;
            }
            boolean key = text.contains("error") || text.contains("Error")
                    || text.contains("Unexpected") || text.contains("expected")
                    || text.startsWith("file:") || text.contains("[vue/compiler-sfc]")
                    || text.contains("failed") || text.contains("Failed");
            if (key) {
                keyLines.append(text).append('\n');
            }
        }
        String summary = keyLines.length() > 0 ? keyLines.toString() : output;
        if (summary.length() > maxLength) {
            summary = summary.substring(summary.length() - maxLength);
        }
        return summary;
    }

    @Override
    public boolean autoRepair(Long appId, User loginUser) {
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isAdmin && !app.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权限操作该应用");
        ThrowUtils.throwIf(!CodeGenTypeEnum.VUE_PROJECT.getValue().equals(app.getCodeGenType()),
                ErrorCode.PARAMS_ERROR, "只有 Vue 工程模式支持自动修复");
        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator
                + CodeGenTypeEnum.VUE_PROJECT.getValue() + "_" + appId;
        // 先构建一次，失败才进入修复流程
        VueProjectBuilder.BuildResult result = vueProjectBuilder.buildProjectWithResult(projectPath);
        if (result.success()) {
            return true;
        }
        // 最多两轮：把构建错误交给模型修复，再重新构建
        for (int attempt = 1; attempt <= 2; attempt++) {
            // 注意：输入护轨限制单条消息不超过 1000 字，这里必须压缩错误日志
            String errorLog = compactBuildError(result.output(), 600);
            String repairPrompt = "项目构建失败，请修复代码，使其能够通过 npm run build。\n"
                    + "请先用文件读取工具查看相关文件，只修改必要的文件，不要重写整个项目。\n\n"
                    + "错误摘要：\n" + errorLog;
            log.info("开始第 {} 轮自动修复，appId：{}", attempt, appId);
            try {
                aiCodeGeneratorFacade.generateAndSaveCodeStream(repairPrompt,
                                CodeGenTypeEnum.VUE_PROJECT, appId)
                        .blockLast(Duration.ofMinutes(10));
            } catch (Exception e) {
                log.error("自动修复调用失败：{}", e.getMessage(), e);
            }
            result = vueProjectBuilder.buildProjectWithResult(projectPath);
            if (result.success()) {
                log.info("第 {} 轮自动修复成功，appId：{}", attempt, appId);
                return true;
            }
        }
        log.error("自动修复失败，appId：{}，最后错误：\n{}", appId, result.output());
        return false;
    }

    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程并执行
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新数据库的封面
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 删除应用时，关联删除对话历史
     *
     * @param id
     * @return
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用关联的对话历史失败：{}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }

    /**
     * 判断用户消息是否只是在询问进度/结果，而不是要求生成或修改
     */
    private boolean isStatusQuestion(String message) {
        if (StrUtil.isBlank(message) || message.length() > 30) {
            return false;
        }
        String text = message.trim();
        // 明确要求修改/生成的动作词，命中则仍然走正常的代码生成流程
        boolean hasAction = StrUtil.containsAny(text,
                "生成", "修改", "改成", "改一下", "添加", "增加", "加上", "写", "创建", "实现",
                "优化", "修复", "继续", "重做", "换成", "删除", "调整", "美化", "改");
        if (hasAction) {
            return false;
        }
        return StrUtil.containsAny(text,
                "完成", "好了", "进度", "怎么样", "在吗", "为什么", "是什么", "多久", "吗", "呢", "？", "?");
    }

    /**
     * 根据磁盘上的文件生成一句项目状态说明（不调用 AI）
     */
    private String buildStatusAnswer(Long appId, CodeGenTypeEnum codeGenTypeEnum) {
        File projectDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR,
                codeGenTypeEnum.getValue() + "_" + appId);
        if (!projectDir.exists()) {
            return "当前应用还没有生成任何内容，请描述你想要的网站，我来开始创建。";
        }
        int[] fileCount = {0};
        long[] latestModified = {0L};
        try (var pathStream = Files.walk(projectDir.toPath())) {
            pathStream.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains("node_modules"))
                    .filter(path -> !path.toString().contains(File.separator + "dist" + File.separator))
                    .forEach(path -> {
                        fileCount[0]++;
                        latestModified[0] = Math.max(latestModified[0], path.toFile().lastModified());
                    });
        } catch (Exception e) {
            log.warn("统计项目文件失败：{}", e.getMessage());
        }
        boolean hasDist = Files.exists(projectDir.toPath().resolve("dist").resolve("index.html"));
        String latestTime = latestModified[0] == 0
                ? "未知"
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(latestModified[0]), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format(
                "当前项目共 %d 个源文件，最近一次更新：%s；构建产物：%s。需要继续生成或修改的话，直接说具体要求即可。",
                fileCount[0], latestTime, hasDist ? "已生成" : "尚未生成");
    }
}
