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
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.SmithingPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.CrankPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.MixPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.PowerPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.PressPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.RunningBeltPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.ExplorerMapPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.HaqiPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.MilkPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.SelfRescuePipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense.TempAdaptPipeline;
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
 */
public final class TaskRegistryManifest {

    /** 注册规格 — (taskType, 构造引用); 构造引用惰性求值 (factory().get()) */
    public record TaskSpec(String taskType, Supplier<TaskPipeline> factory) {}

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
            // v79.62 锻造: 钻石装备+下界合金锭→下界合金装备 (无需模板)
            new TaskSpec("smithing", SmithingPipeline::new),
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
     * 被动任务管线 (4) — PassiveSenseRegistration.init 注册 (哈气默认关闭 — HAQI_ENABLED 门控)。
     * 纯触发型 2 (structure_sense/festival) 已脱管线 (v79.61x) — 无 pipeline 占位 + PassiveDispatcher,
     * 不在本表 (见 init 分派)。v79.62: snow_shovel 已删 (TLM 原版清雪覆盖, 用户裁定)。
     */
    public static final List<TaskSpec> PASSIVE = List.of(new TaskSpec[]{
            new TaskSpec("temp_adapt", TempAdaptPipeline::new),
            // 哈气 (默认关闭 — HAQI_ENABLED 门控; 触发走 MAID_NEARBY 信号; 底层覆盖 — 运行中其他被动不执行)
            new TaskSpec("haqi", HaqiPipeline::new),
            // v79.47: 黑暗自动点亮 (DARKNESS → 副手火把/提灯)
            new TaskSpec("torch_light", TorchLightPipeline::new),
            // v79.58: 自救被动 (掉血触发 → 被埋瞬破; 暂停主动任务不清理数据, 自救完恢复)
            new TaskSpec("self_rescue", SelfRescuePipeline::new),
            // v79.62 奶桶喂食 (空管线占位, 默认关闭)
            new TaskSpec("jiuhu_milk", MilkPipeline::new),
            // v79.62 探险家地图: 检测到背包有探险家地图 → 气泡报宝藏坐标 (一次)
            new TaskSpec("explorer_map", ExplorerMapPipeline::new),
    });

    private TaskRegistryManifest() {}
}
