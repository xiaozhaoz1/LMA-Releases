package com.github.xiaozhaoz1.littlemaidmoreaction.task;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.ArmTransferPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.BellRingPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.BlockInteractPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.BrushPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.ChainHarvestPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.FurnacePipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.FarmPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.JukeboxPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.CrankPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.MixPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.PowerPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.PressPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.RunningBeltPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.ExplorerMapPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.JiuhuMilkPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.SelfRescuePipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.TorchLightPipeline;

import java.util.List;
import java.util.function.Supplier;

/**
 * 任务注册规格表 (v79.61 架构批 3c C3 → 规格化: 名字+构造引用单一真相, 名字不再抄两遍)。
 *
 * <p>TaskRegistry / PassiveSenseRegistration 按本表循环注册; 门控 (CompatToggle + ModList)
 * 与注册时序保留在各注册点原位 (2026-08-11c 裁定: 统一门控回调因双时序上下文已驳)。
 * 本类零 MC 依赖 (构造引用惰性, 不触发管线类初始化) — 纯 JVM 可测 (错题 #174 铁律)。
 *
 * <p>规格表按门控分组; 顺序 = 注册顺序 = 任务树展示顺序 (LinkedHashMap), 改序即改显示。
 * 变体任务 (如 collect_wood/collect_ore) 用构造参数化 — 一个类一个构造参数, 不建工厂类
 * (v79.61 架构裁定, ChainHarvestPipeline 先例)。
 *
 * <p><b>v79.63 Drive (驱动模式) — 本表是「谁 tick 我」的唯一真相源:</b>
 * 驱动模式曾由引擎侧 3 处手写 {@code String} 集合维护 (GMPM_PIPELINES/STANDALONE), 漏加一个名字
 * 即**静默死链** (注册了、可配置、测试直调仍绿, 但生产从不 tick — 错题 #157 同型, 2026-09-11 实测
 * jiuhu_milk/explorer_map 两条死链)。现改为本表声明 + {@code TaskRegistry.verifyManifest} 启动期
 * fail-fast + {@code GameTickPipelineManager} 按 Drive 分派 — 漏声明 = **启动即炸**, 不再静默。
 */
public final class TaskRegistryManifest {

    /**
     * 驱动模式 — 决定「谁 tick 我」。新增任务必须显式选择 (主动组默认 {@link #ACTIVE})。
     *
     * <p>{@link #isPassive()} = 不挂 TLM 任务栏; {@code TaskRegistry.registerPassive} 侧校验必须为被动类。
     */
    public enum Drive {
        /** 主动任务: TLM 任务栏提交 → {@code GameTickPipelineManager.tickActive} */
        ACTIVE,
        /** 被动: {@code tickPassiveFor} 驱动 (FSM/时间关键; 坐下不暂停) */
        GMPM_PASSIVE,
        /** 被动: {@code tickStandalonePassives} 独立心跳 (动作型, 管线内节流自持; 坐下暂停) */
        STANDALONE_PASSIVE,
        /** 被动: 无 tick — {@code PassiveDispatcher} 信号驱动 (无 pipeline 占位条目) */
        TRIGGER_PASSIVE;

        /** 是否被动驱动 (无 TLM 任务栏条目) */
        public boolean isPassive() { return this != ACTIVE; }
    }

    /** 注册规格 — (taskType, 构造引用, 驱动模式); 构造引用惰性求值 (factory().get()) */
    public record TaskSpec(String taskType, Supplier<TaskPipeline> factory, Drive drive) {
        /** 主动任务便捷构造 (drive = ACTIVE) — 主动组 21 条零重复声明 */
        public TaskSpec(String taskType, Supplier<TaskPipeline> factory) {
            this(taskType, factory, Drive.ACTIVE);
        }
    }

    /** 无条件注册的主动任务 (9) — TaskRegistry clinit 恒注册 */
    public static final List<TaskSpec> ALWAYS = List.of(new TaskSpec[]{
            new TaskSpec("craft_chain", CraftChainPipeline::new),
            new TaskSpec("furnace", FurnacePipeline::new),
            new TaskSpec("jukebox", JukeboxPipeline::new),
            new TaskSpec("bell_ring", BellRingPipeline::new),
            new TaskSpec("collect_wood", () -> new ChainHarvestPipeline(ChainHarvestPipeline.Mode.WOOD)),
            new TaskSpec("collect_ore", () -> new ChainHarvestPipeline(ChainHarvestPipeline.Mode.ORE)),
            new TaskSpec("arm_transfer", ArmTransferPipeline::new),
            new TaskSpec("block_interact", BlockInteractPipeline::new),
            // v79.62 区域制种菜 (框选区域绑定女仆 → 收成熟+种指定)
            new TaskSpec("farm", FarmPipeline::new),
            // v79.62.2 填坝排水: 大海排水前置 — 外圈筑重力方块墙 + 排空区域内水
            new TaskSpec("dam_fill", com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.DamFillPipeline::new),
            // v79.62 刷子: 女仆自动刷可疑方块 (沙子/沙砾 → 掉落)
            new TaskSpec("brush", BrushPipeline::new),
            // v79.62.1 篝火: 女仆自动烤食物 (放生食+捡熟食掉落)
            new TaskSpec("campfire", com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CampfirePipeline::new),
            // v79.62 挖空置域: 女仆把大区域从基岩上到世界高度全挖空 (起点+区块数, 逐层下降)
            new TaskSpec("void_excavation", com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.VoidExcavationPipeline::new),
    });

    /** Numen 门控 (1) — AI 对话来源 (CompatToggle numen + NumenCompat.isInstalled) */
    public static final List<TaskSpec> NUMEN = List.of(new TaskSpec[]{
            new TaskSpec("ai_control", AiControlPipeline::new),
    });

    /** Create 门控 (6) — 女仆专属任务 (CompatToggle create + ModList create) */
    public static final List<TaskSpec> CREATE = List.of(new TaskSpec[]{
            new TaskSpec("crank", CrankPipeline::new),
            new TaskSpec("power", PowerPipeline::new),
            new TaskSpec("press", PressPipeline::new),
            new TaskSpec("mix", MixPipeline::new),
            new TaskSpec("running_belt", RunningBeltPipeline::new),
            new TaskSpec("maid_assembly", MaidAssemblyPipeline::new),
    });

    /** Create Big Cannons 门控 (1) — 双平台 (2026-08-16 移植; 条件化条目; 数组初始化器防尾随逗号 #175) */
    public static final List<TaskSpec> CBC = List.of(new TaskSpec[]{
//? if 1.20.1 {
            new TaskSpec("cannon_load", com.github.xiaozhaoz1.littlemaidmoreaction.compat.createbigcannons.task.CannonLoadPipeline::new),
//?} else {
            new TaskSpec("cannon_load", com.github.xiaozhaoz1.littlemaidmoreaction.compat.createbigcannons.task.CannonLoadPipeline::new),
//?}
    });

    /**
     * 被动任务管线 (5) — PassiveSenseRegistration.init 注册。
     * 纯触发型 3 (structure_sense/festival/rare_biome) 已脱管线 (v79.61x) — 无 pipeline 占位
     * + PassiveDispatcher (TRIGGER_PASSIVE), 不在本表 (见 init 分派)。
     * v79.62: snow_shovel 删; v79.62.5: temp_adapt 删 (TLM 温度机制覆盖, 用户裁定)。
     *
     * <p><b>2026-09-11 修复</b>: 原 jiuhu_milk/explorer_map 未进任何引擎驱动集合 = 生产死链
     * (测试直调掩盖)。现显式声明 Drive: jiuhu_milk = STANDALONE (动作型 + 管线内节流, 坐下暂停);
     * explorer_map = GMPM (纯信息气泡, 坐下不暂停 — 用户裁定)。
     */
    public static final List<TaskSpec> PASSIVE = List.of(new TaskSpec[]{
            // 哈气 (默认关闭 — HAQI_ENABLED 门控; 触发走 MAID_NEARBY 信号; 底层覆盖 — 运行中其他被动不执行)
            new TaskSpec("haqi", HaqiPipeline::new, Drive.GMPM_PASSIVE),
            // v79.47: 黑暗自动点亮 (DARKNESS → 副手火把/提灯; 动作型 — 坐下暂停)
            new TaskSpec("torch_light", TorchLightPipeline::new, Drive.STANDALONE_PASSIVE),
            // v79.58: 自救被动 (掉血触发 → 被埋瞬破; 时间关键 — 与主动任务并行)
            new TaskSpec("self_rescue", SelfRescuePipeline::new, Drive.GMPM_PASSIVE),
            // v79.62 奶桶喂食 (per-maid 开关; 动作型 — 主人低血喂奶, 坐下暂停)
            new TaskSpec("jiuhu_milk", JiuhuMilkPipeline::new, Drive.STANDALONE_PASSIVE),
            // v79.62 探险家地图: 检测到背包有探险家地图 → 气泡报宝藏坐标 (一次; 纯信息, 不受坐下限制)
            new TaskSpec("explorer_map", ExplorerMapPipeline::new, Drive.GMPM_PASSIVE),
    });

    private TaskRegistryManifest() {}
}
