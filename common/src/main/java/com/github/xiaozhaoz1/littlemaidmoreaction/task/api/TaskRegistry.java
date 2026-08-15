package com.github.xiaozhaoz1.littlemaidmoreaction.task.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务注册中心 (v53: 注册简化 — 每个任务一行 register(name, new Pipeline())).
 * v79.28: 注册去 executor 参数 — executor 从 pipeline.executor() 取 (默认 = tick 委托).
 * v79.32: executor 概念删除 — TaskHandler 无 executor 字段 (执行经 Brain doExecute 驱动).
 * v79.45: execute 删除 — 执行全归 GMPM tick (工作站走 WorkStationPipeline 节拍, 移动型自写 tick).
 * 外部 Mod 注册方式见 {@link #register(String, TaskPipeline)} (统一门面 {@code LMAT})
 */
public final class TaskRegistry {

    private static final Map<String, TaskHandler> HANDLERS = new LinkedHashMap<>();

    /** 被动任务缓存 — register() 重建 (唯一写入口实证); 避免每女仆每 tick 新建 Stream */
    private static volatile List<TaskHandler> passiveCache = List.of();

    static {
        // ── 无条件注册 — 规格表驱动 (TaskRegistryManifest.ALWAYS: 名字+构造引用单一真相, 顺序=任务树顺序) ──
        for (TaskRegistryManifest.TaskSpec s : TaskRegistryManifest.ALWAYS) {
            register(s.taskType(), s.factory().get());
        }

        // ── AI 操控 — 依赖 Numen (AI 对话来源 + 假人桥); 未装 Numen 不注册 (任务不出现) ──
        // CompatToggle 开关 (可 GUI 关闭)
        // ★ 门控镜像 CompatRegistry.MODULES 模块表 (GUI/开关单一事实源) — 2026-08-11c
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("numen")
                && com.github.xiaozhaoz1.littlemaidmoreaction.compat.NumenCompat.isInstalled()) {
            for (TaskRegistryManifest.TaskSpec s : TaskRegistryManifest.NUMEN) {
                register(s.taskType(), s.factory().get());
            }
        }

        // ── Create 女仆专属任务 (4 基础管线; running_belt/assembly 双平台化; cbc 1.20.1 仅) ──
        // CompatToggle 开关 (可 GUI 关闭)
        // ★ 门控镜像 CompatRegistry.MODULES 模块表 (GUI/开关单一事实源) — 2026-08-11c
//? if 1.20.1 {
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")
                && net.minecraftforge.fml.ModList.get().isLoaded("create")) {
//?} else {
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")
                && net.neoforged.fml.ModList.get().isLoaded("create")) {
//?}
            for (TaskRegistryManifest.TaskSpec s : TaskRegistryManifest.CREATE) {
                register(s.taskType(), s.factory().get());
            }
        }

//? if 1.20.1 {
        // ── Create Big Cannons 速射炮闩装填 (1.20.1 仅) ──
        // CompatToggle 开关 (可 GUI 关闭)
        // ★ 门控镜像 CompatRegistry.MODULES 模块表 (GUI/开关单一事实源) — 2026-08-11c
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("createbigcannons")
                && net.minecraftforge.fml.ModList.get().isLoaded("createbigcannons")) {
            for (TaskRegistryManifest.TaskSpec s : TaskRegistryManifest.CBC) {
                register(s.taskType(), s.factory().get());
            }
        }
//?}
        verifyManifest();
    }

    /**
     * 注册任务 — showInBar 参数删除 (主动/被动由注册 API 区分, 可见性由任务树
     * TaskToggle.isVisible 运行期管理)。主动任务出现在 TLM 任务栏 GUI。
     */
    public static void register(String taskType, TaskPipeline pipeline) {
        if (HANDLERS.containsKey(taskType)) {
            throw new IllegalStateException("[LMA] 任务重复注册: " + taskType);
        }
        HANDLERS.put(taskType, new TaskHandler(taskType, pipeline, true));
        rebuildPassiveCache();
    }

    /** 被动缓存重建 — register 是 HANDLERS 唯一写入口 (LMAT.register/LMAT.registerPassive 全汇聚于此) */
    private static void rebuildPassiveCache() {
        passiveCache = HANDLERS.values().stream().filter(h -> !h.showInBar()).toList();
    }

    /** 注册被动任务 (内部 showInBar=false — 不显示在 TLM 任务栏, 由事件/环境信号触发) */
    public static void registerPassive(String taskType, TaskPipeline pipeline) {
        if (HANDLERS.containsKey(taskType)) {
            throw new IllegalStateException("[LMA] 被动任务重复注册: " + taskType);
        }
        HANDLERS.put(taskType, new TaskHandler(taskType, pipeline, false));
        rebuildPassiveCache();
    }

    /**
     * 注册完整性 fail-fast (v79.61 批 3c C3) — 无条件任务必须全注册,
     * 漂移 (改名/漏注册) 启动即炸 (PacketRegistry.validatePlatformNames 同款防线)。
     */
    private static void verifyManifest() {
        for (TaskRegistryManifest.TaskSpec s : TaskRegistryManifest.ALWAYS) {
            if (!HANDLERS.containsKey(s.taskType())) {
                throw new IllegalStateException("[LMA] 任务注册缺失: " + s.taskType());
            }
        }
    }

    public static PipelineResult validate(EntityMaid maid, String taskType, String taskId,
                                          String target, int targetCount) {
        TaskHandler handler = HANDLERS.get(taskType);
        if (handler == null) return PipelineResult.failed("未知任务类型: " + taskType);
        if (!(maid.level() instanceof ServerLevel level)) return PipelineResult.failed("仅在服务端可用");
        return handler.pipeline().validate(level, maid, new PipelineContext(target, targetCount, taskId));
    }

    public static TaskHandler get(String taskType) { return HANDLERS.get(taskType); }
    public static Set<String> taskTypes() { return HANDLERS.keySet(); }

    /** 是否在 TLM 任务栏显示 */
    public static boolean isShowInBar(String taskType) {
        TaskHandler h = HANDLERS.get(taskType);
        return h != null && h.showInBar();
    }

    /** 被动任务缓存列表 — 每 level hoist 一次, 避免每女仆每 tick 新建 Stream */
    public static List<TaskHandler> passiveTasksList() {
        return passiveCache;
    }

    /** executor 字段删除 — 执行归管线 (GMPM tick / WorkStationPipeline) */
    public record TaskHandler(String taskType, TaskPipeline pipeline, boolean showInBar) {}
}
