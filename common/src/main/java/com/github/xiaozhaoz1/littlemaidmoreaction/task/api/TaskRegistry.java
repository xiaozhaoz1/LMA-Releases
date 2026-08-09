package com.github.xiaozhaoz1.littlemaidmoreaction.task.api;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.io.IExecutor;
// v73: Create 4 基础管线双平台 (compat.create.task 迁入 common); cbc 1.20.1 仅
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.*;
//? if 1.20.1 {
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.createbigcannons.task.CannonLoadPipeline;
//?}
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.*;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务注册中心 (v53: 注册简化 — 每个任务一行 register(name, new Pipeline(), Pipeline.executor()))
 * 外部 Mod 注册方式见 {@link #register(String, TaskPipeline, IExecutor, boolean)}
 */
public final class TaskRegistry {

    private static final Map<String, TaskHandler> HANDLERS = new LinkedHashMap<>();

    /** v79: 被动任务缓存 — register() 重建 (唯一写入口实证); 避免每女仆每 tick 新建 Stream */
    private static volatile List<TaskHandler> passiveCache = List.of();

    static {
        register("craft_chain",  new CraftChainPipeline(),  CraftChainPipeline.executor());
        register("furnace",      new FurnacePipeline(),      FurnacePipeline.executor());
        register("jukebox",      new JukeboxPipeline(),      JukeboxPipeline.executor());
        register("bell_ring",    new BellRingPipeline(),     new BellRingPipeline().executor());
        register("collect_wood", new ChainWoodPipeline(),    new ChainWoodPipeline().executor());
        register("collect_ore",  new ChainOrePipeline(),     new ChainOrePipeline().executor());
        var armTransferPl = new ArmTransferPipeline();
        register("arm_transfer", armTransferPl, armTransferPl.executor());

        // ── v66: 女仆右键交互 ──
        var blockInteractPl = new BlockInteractPipeline();
        register("block_interact", blockInteractPl, blockInteractPl.executor());

        // ── v73/v75.3: AI 操控 — 依赖 Numen (AI 对话来源 + 假人桥); 未装 Numen 不注册 (任务不出现) ──
        // v77: CompatToggle 开关 (可 GUI 关闭)
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("numen")
                && com.github.xiaozhaoz1.littlemaidmoreaction.compat.NumenCompat.isInstalled()) {
            var aiControlPl = new com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline();
            register("ai_control", aiControlPl, aiControlPl.executor());
        }

        // ── v38-40: Create 女仆专属任务 (v73: 4 基础管线; v75.1: running_belt/assembly 双平台化; cbc 1.20.1 仅) ──
        // v77: CompatToggle 开关 (可 GUI 关闭)
//? if 1.20.1 {
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")
                && net.minecraftforge.fml.ModList.get().isLoaded("create")) {
//?} else {
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")
                && net.neoforged.fml.ModList.get().isLoaded("create")) {
//?}
            var crankPl = new CrankPipeline();
            register("crank", crankPl, crankPl.executor());

            var powerPl = new PowerPipeline();
            register("power", powerPl, powerPl.executor());

            var pressPL = new PressPipeline();
            register("press", pressPL, pressPL.executor());

            var mixPL = new MixPipeline();
            register("mix", mixPL, mixPL.executor());

            var beltPl = new RunningBeltPipeline();
            register("running_belt", beltPl, beltPl.executor());

            var assemblyPl = new com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyPipeline();
            register("maid_assembly", assemblyPl, assemblyPl.executor(), true);
        }

//? if 1.20.1 {
        // ── Create Big Cannons 速射炮闩装填 (1.20.1 仅) ──
        // v77: CompatToggle 开关 (可 GUI 关闭)
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("createbigcannons")
                && net.minecraftforge.fml.ModList.get().isLoaded("createbigcannons")) {
            var cannonLoadPl = new CannonLoadPipeline();
            register("cannon_load", cannonLoadPl, cannonLoadPl.executor());
        }
//?}
    }

    /**
     * v52: 注册任务 — showInBar=true 的任务会出现在 TLM 任务栏 GUI。
     * 被动/环境任务应传 false，只内部注册不暴露给玩家。
     */
    public static void register(String taskType, TaskPipeline pipeline, IExecutor executor,
                                 boolean showInBar) {
        HANDLERS.put(taskType, new TaskHandler(taskType, pipeline, executor, showInBar));
        rebuildPassiveCache();
    }

    /** v79: 被动缓存重建 — register 是 HANDLERS 唯一写入口 (LMAT.register/LMAT.registerPassive 全汇聚于此) */
    private static void rebuildPassiveCache() {
        passiveCache = HANDLERS.values().stream().filter(h -> !h.showInBar()).toList();
    }

    /** v52: 默认 showInBar=true — 大多数任务在 TLM 任务栏可见 */
    public static void register(String taskType, TaskPipeline pipeline, IExecutor executor) {
        register(taskType, pipeline, executor, true);
    }

    /** v63: 注册被动任务 (showInBar=false), 供环境感知管线使用 */
    public static void registerPassive(String taskType, TaskPipeline pipeline) {
        register(taskType, pipeline, passiveExecutor(), false);
    }

    private static IExecutor passiveExecutor() {
        return (w, m, p, d) ->
                com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult.CONTINUE;
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

    /** v52: 是否在 TLM 任务栏显示 */
    public static boolean isShowInBar(String taskType) {
        TaskHandler h = HANDLERS.get(taskType);
        return h != null && h.showInBar();
    }

    /** v52: showInBar=true → TLM 任务栏可见; false → 仅内部注册 (被动/环境任务) */
    /** showInBar=false 的被动任务 (v61). v79: 返回缓存流 (零过滤开销). */
    public static java.util.stream.Stream<TaskHandler> passiveTasks() {
        return passiveCache.stream();
    }

    /** v79: 被动任务缓存列表 — 每 level hoist 一次, 避免每女仆每 tick 新建 Stream */
    public static List<TaskHandler> passiveTasksList() {
        return passiveCache;
    }

    public record TaskHandler(String taskType, TaskPipeline pipeline, IExecutor executor,
                               boolean showInBar) {}
}
