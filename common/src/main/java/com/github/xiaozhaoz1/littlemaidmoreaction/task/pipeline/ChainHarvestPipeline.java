package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolStateReader;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ToolJudge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 连锁采集管道 (v79.38 合并 ChainOre/ChainWood — 参数化 Mode) — 砍树 (collect_wood) / 挖矿 (collect_ore)。
 * 实际搜索/导航/破坏由 ChainHarvestExecute 执行。
 *
 * <p>v79.34: 装饰性状态机简化 — 直接实现 TaskPipeline 每 tick 直执行。
 * v79.38: ChainOrePipeline/ChainWoodPipeline 近全同类(仅 Mode/目标 tag/工具/文案不同)→ 合并本类参数化。
 *
 * <p>v79.61 架构裁定: 本类是"同类任务多变体 → 一个类 + 构造参数"的标准姿势 (先例) —
 * 变体工厂退化为构造注入, 不建工厂类 (全项目任务均为无参 new 或单参数构造, 域工厂已证过度设计)。
 */
public final class ChainHarvestPipeline implements TaskPipeline, TaskConfigurable {

    /** 采集模式 — ORE 挖矿 (镐) / WOOD 砍树 (斧) */
    public enum Mode { ORE, WOOD }

    private final Mode mode;

    public ChainHarvestPipeline(Mode mode) {
        this.mode = mode;
    }

    private boolean isOre() { return mode == Mode.ORE; }

    @Override public String taskType() { return isOre() ? "collect_ore" : "collect_wood"; }
    @Override public boolean isLongRunning() { return true; }

    // 2026-08-11c 注释 (全景 #18): 双模式并存 — isTargetBlock (tag 判定) 供 Brain 导航
    // 匹配寻路目标; tick 自行搜索 (ChainHarvestExecute BlockScanner 扫描) 独立于导航 —
    // 两通道互补 (导航负责走到, 扫描负责开脉), 非冗余。
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) {
//? if 1.20.1 {
        return isOre() ? s.is(net.minecraftforge.common.Tags.Blocks.ORES) : s.is(net.minecraft.tags.BlockTags.LOGS);
//?} else {
        return isOre() ? s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES) : s.is(net.minecraft.tags.BlockTags.LOGS);
//?}
    }

    /** 每 tick 直执行 — 取消检查 + ChainHarvestExecute */
    @Override
    public void tick(ServerLevel world, EntityMaid maid) {
        if (TaskKeys.STATE_CANCELLED.equals(FlowTaskData.getState(maid))) return;
        ChainHarvestExecute.execute(world, maid, maid.blockPosition(),
                maid.getPersistentData(), isOre() ? ChainHarvestExecute.Mode.ORE : ChainHarvestExecute.Mode.WOOD);
    }

    /** 缓存管理: 终结路径汇聚 — 清挖矿静态缓存 */
    @Override
    public void onCleanup(EntityMaid maid) {
        ChainHarvestExecute.clearMaidState(maid);
        TaskPipeline.super.onCleanup(maid);
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        ItemStack tool = maid.getMainHandItem();
        if (isOre()) {
            // v79.53: 主手非镐 → 查背包 (execute 会自动换镐 — 原仅查主手:
            // 主手空/拿剑 + 背包有镐 = validate 失败任务永不启动, 用户实测)
            boolean mainOk = ToolStateReader.isPickaxe(tool)
                    && ToolJudge.isToolUsable(tool, com.github.xiaozhaoz1.littlemaidmoreaction.task.service.HarvestTarget.TOOL_RESERVE_DURABILITY);
            if (!mainOk) {
                var pick = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ItemSelect.selectBest(maid,
                        s -> ToolStateReader.isPickaxe(s) && ToolJudge.isToolUsable(s, com.github.xiaozhaoz1.littlemaidmoreaction.task.service.HarvestTarget.TOOL_RESERVE_DURABILITY),
                        s -> ToolStateReader.getTierLevel(s));
                if (pick.isEmpty()) {
                    return PipelineResult.failed("背包没有可用的镐");
                }
                return PipelineResult.ok("背包有镐, 将自动装备");
            }
            return PipelineResult.ok("开始连锁挖矿");
        }
        // 无斧不拦截 — 慢砍模式（斧影响速度而非可行性）
        if (!ToolStateReader.isAxe(tool)) {
            return PipelineResult.ok("无斧慢砍模式（持斧砍伐更快且更耐用）");
        }
        if (!ToolJudge.isToolUsable(tool, com.github.xiaozhaoz1.littlemaidmoreaction.task.service.HarvestTarget.TOOL_RESERVE_DURABILITY)) {
            return PipelineResult.ok("斧即将损坏，将以慢砍模式作业");
        }
        return PipelineResult.ok("开始连锁砍树");
    }

    /** 单女仆采集名单配置 (TLM 任务设置标签页) */
    @Override
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, taskType());
    }

    @Override
    public List<TaskStep> steps() {
        return isOre()
                ? List.of(
                        new TaskStep("search", "寻找矿石", StepType.COLLECT, List.of()),
                        // v79.58: 接近阶段 (用户: "流程显示没加") — 覆盖 navigate 全路径:
                        // 水平 TLM 走近 / 头顶斜上走到矿下 / digUp 向上挖穿 (任务树 GUI 显示)
                        new TaskStep("approach", "走向矿脉", StepType.COLLECT, List.of("search")),
                        new TaskStep("mine", "连锁挖掘", StepType.INTERACT, List.of("approach")))
                : List.of(
                        new TaskStep("search", "寻找树木", StepType.COLLECT, List.of()),
                        new TaskStep("chop", "连锁砍伐", StepType.INTERACT, List.of("search")));
    }
}
