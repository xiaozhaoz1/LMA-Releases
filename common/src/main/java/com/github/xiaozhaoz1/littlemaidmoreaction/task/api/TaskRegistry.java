package com.github.xiaozhaoz1.littlemaidmoreaction.task.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.TaskRegistryManifest;
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

    /**
     * 注册表 — **写侧** (仅 register* 在锁内写; 保留插入顺序 = 任务树展示顺序)。
     * v79.63 并发修复 (评审 P1-5): 原为裸 LinkedHashMap + 无同步 register, 而 {@code LMAT}
     * javadoc **明确允许"更晚注册"**(外部 mod 可在任意时机注册) → 与服务端 tick 期的读/遍历
     * 并发即 CME / 丢更新。现改为「写侧加锁 + 读侧 volatile 不可变快照」:
     * <ul>
     *   <li>写: {@link #REGISTER_LOCK} 串行化 (插入顺序稳定, 无需 ConcurrentHashMap 丢序)</li>
     *   <li>读: {@link #SNAPSHOT} / {@link #passiveCache} — volatile 不可变视图, 无锁无 CME</li>
     * </ul>
     */
    private static final Map<String, TaskHandler> HANDLERS = new LinkedHashMap<>();

    /** 注册锁 — 串行化注册 (注册频次极低: 启动期 + 外部 mod 注册) */
    private static final Object REGISTER_LOCK = new Object();

    /** 读侧快照 — 每次注册后整体替换 (volatile 发布; 读方永不看到半成品/并发修改) */
    private static volatile Map<String, TaskHandler> SNAPSHOT = Map.of();

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
        // ── Create Big Cannons 速射炮闩装填 ──
        // CompatToggle 开关 (可 GUI 关闭)
        // ★ 门控镜像 CompatRegistry.MODULES 模块表 (GUI/开关单一事实源) — 2026-08-11c
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("createbigcannons")
                && net.minecraftforge.fml.ModList.get().isLoaded("createbigcannons")) {
            for (TaskRegistryManifest.TaskSpec s : TaskRegistryManifest.CBC) {
                register(s.taskType(), s.factory().get());
            }
        }
//?} else {
        // ── Create Big Cannons 速射炮闩装填 (1.21.1 移植 2026-08-16) ──
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("createbigcannons")
                && net.neoforged.fml.ModList.get().isLoaded("createbigcannons")) {
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
        register(taskType, pipeline, TaskRegistryManifest.Drive.ACTIVE);
    }

    /** 注册任务 (显式驱动模式) — 主动任务必须 {@code Drive.ACTIVE} (registerPassive 侧拒绝被动类) */
    public static void register(String taskType, TaskPipeline pipeline, TaskRegistryManifest.Drive drive) {
        if (drive.isPassive()) {
            throw new IllegalStateException("[LMA] 主动注册不接受被动驱动: " + taskType + " drive=" + drive);
        }
        putLocked(taskType, new TaskHandler(taskType, pipeline, true, drive), "任务");
    }

    /** 被动缓存重建 — register 是 HANDLERS 唯一写入口 (LMAT.register/LMAT.registerPassive 全汇聚于此)。
     *  必须在 {@link #REGISTER_LOCK} 内调用 (读 HANDLERS 写两个 volatile 快照)。
     *  注意用 {@code unmodifiableMap(new LinkedHashMap<>())} 而非 {@code Map.copyOf} —
     *  后者**不保证迭代顺序**, 会破坏"注册顺序 = 任务树展示顺序"不变量。 */
    private static void rebuildPassiveCache() {
        passiveCache = HANDLERS.values().stream().filter(h -> !h.showInBar()).toList();
        SNAPSHOT = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(HANDLERS));
    }

    /** 注册写点 (锁内: 重名检查 + 写入 + 快照重建) — 三个 register* 唯一汇聚处 */
    private static void putLocked(String taskType, TaskHandler handler, String kind) {
        synchronized (REGISTER_LOCK) {
            if (HANDLERS.containsKey(taskType)) {
                throw new IllegalStateException("[LMA] " + kind + "重复注册: " + taskType);
            }
            HANDLERS.put(taskType, handler);
            rebuildPassiveCache();
        }
    }

    /** 注册被动任务 (内部 showInBar=false — 不显示在 TLM 任务栏, 由事件/环境信号触发) */
    public static void registerPassive(String taskType, TaskPipeline pipeline) {
        registerPassive(taskType, pipeline, TaskRegistryManifest.Drive.GMPM_PASSIVE);
    }

    /**
     * 注册被动任务 (显式驱动模式) — drive 必须为被动类, 否则启动即炸 (v79.63 Drive 单一真相源:
     * 驱动模式漏声明/声明错 = 静默死链, 故在此 fail-fast)。
     */
    public static void registerPassive(String taskType, TaskPipeline pipeline,
                                       TaskRegistryManifest.Drive drive) {
        if (!drive.isPassive()) {
            throw new IllegalStateException("[LMA] 被动注册必须声明被动驱动: " + taskType + " drive=" + drive);
        }
        putLocked(taskType, new TaskHandler(taskType, pipeline, false, drive), "被动任务");
    }

    /**
     * 注册纯触发型被动 (v79.61x 脱管线 — 无 pipeline 占位条目, 用户裁定)。
     * 条目仅供任务树可见 + TaskToggle 开关; 执行经 {@code PassiveDispatcher} (task/passive),
     * 不经 GMPM tick (从不写 in_progress 键)。
     */
    public static void registerPassive(String taskType) {
        registerPassive(taskType, null, TaskRegistryManifest.Drive.TRIGGER_PASSIVE);
    }

    /**
     * 注册完整性 fail-fast (v79.61 批 3c C3 → v79.63 扩 Drive 校验) — 无条件任务必须全注册,
     * 且**驱动模式必须与注册面一致** (漂移 = 静默死链, 启动即炸;
     * PacketRegistry.validatePlatformNames 同款防线)。
     */
    private static void verifyManifest() {
        for (TaskRegistryManifest.TaskSpec s : TaskRegistryManifest.ALWAYS) {
            if (!SNAPSHOT.containsKey(s.taskType())) {
                throw new IllegalStateException("[LMA] 任务注册缺失: " + s.taskType());
            }
            TaskHandler h = SNAPSHOT.get(s.taskType());
            if (h != null && h.drive() != s.drive()) {
                throw new IllegalStateException("[LMA] 任务驱动漂移: " + s.taskType()
                        + " 声明=" + s.drive() + " 注册=" + h.drive());
            }
        }
        // 被动表: 必须是被动驱动 + 注册面一致 (v79.63 — jiuhu_milk/explorer_map 死链教训)
        for (TaskRegistryManifest.TaskSpec s : TaskRegistryManifest.PASSIVE) {
            if (!s.drive().isPassive()) {
                throw new IllegalStateException("[LMA] 被动表条目必须声明被动驱动: "
                        + s.taskType() + " drive=" + s.drive());
            }
        }
    }

    public static PipelineResult validate(EntityMaid maid, String taskType, String taskId,
                                          String target, int targetCount) {
        TaskHandler handler = SNAPSHOT.get(taskType);
        if (handler == null) return PipelineResult.failed("未知任务类型: " + taskType);
        if (handler.pipeline() == null) return PipelineResult.failed("无管道任务: " + taskType);
        if (!(maid.level() instanceof ServerLevel level)) return PipelineResult.failed("仅在服务端可用");
        return handler.pipeline().validate(level, maid, new PipelineContext(target, targetCount, taskId));
    }

    public static TaskHandler get(String taskType) { return SNAPSHOT.get(taskType); }

    /**
     * 全部任务类型 — 返回**不可变快照** (v79.63: 原直返 {@code HANDLERS.keySet()} 是内部活视图,
     * 外部/客户端屏遍历时会随注册变动 → CME 风险; 且暴露内部结构)。
     */
    public static Set<String> taskTypes() { return SNAPSHOT.keySet(); }

    /** 是否在 TLM 任务栏显示 */
    public static boolean isShowInBar(String taskType) {
        TaskHandler h = SNAPSHOT.get(taskType);
        return h != null && h.showInBar();
    }

    /** 被动任务缓存列表 — 每 level hoist 一次, 避免每女仆每 tick 新建 Stream */
    public static List<TaskHandler> passiveTasksList() {
        return passiveCache;
    }

    /**
     * 按驱动模式取被动条目 (v79.63 Drive 分派) — 引擎按此分桶, 取代原引擎侧手写 String 集合。
     * 返回不可变快照 (调用方每 tick 遍历 — 不暴露内部视图)。
     */
    public static List<TaskHandler> passivesByDrive(TaskRegistryManifest.Drive drive) {
        return passiveCache.stream().filter(h -> h.drive() == drive).toList();
    }

    /**
     * executor 字段删除 — 执行归管线 (GMPM tick / WorkStationPipeline)。
     * v79.63: 增 {@code drive} (驱动模式, 来自 manifest — 「谁 tick 我」的唯一真相源);
     * {@code pipeline} 仅 TRIGGER_PASSIVE 占位条目为 null。
     */
    public record TaskHandler(String taskType, TaskPipeline pipeline, boolean showInBar,
                              TaskRegistryManifest.Drive drive) {
        /** 纯触发型占位条目 (无 pipeline) */
        public boolean isTriggerPlaceholder() { return pipeline == null; }
    }
}
