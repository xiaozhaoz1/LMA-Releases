package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.io.IExecutor;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item.ToolStateReader;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineContext;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.PipelineResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigGuiFactory;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateMachine;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.TaskStep;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline.StepType;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.ToolJudge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 连锁挖矿管道 (v36) — 校验主手镐可用。
 * 挖掘等级判定（镐材质 vs 矿石门槛）在到达矿脉后由 ToolJudge.canPickaxeMine 逐块执行。
 *
 * <p>v79.7 状态机化: SEARCHING/CHOPPING 双态 — 阶段由执行数据镜像 (同 ChainWood), 行为零变化。
 */
public final class ChainOrePipeline extends TaskStateMachine<ChainOrePipeline.State> {

    enum State { SEARCHING, CHOPPING }

    /** 状态处理器表 — 每状态独立方法 (v79.7 示范模式) */
    private static final Map<State, Function<StateCtx, State>> HANDLERS = Map.of(
            State.SEARCHING, ChainOrePipeline::handleSearching,
            State.CHOPPING,  ChainOrePipeline::handleChopping);

    private record StateCtx(EntityMaid maid, ServerLevel world, CompoundTag data) {}

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "collect_ore"; }
    @Override public boolean needsGameTick() { return true; }
    @Override public boolean isLongRunning() { return true; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
                State.SEARCHING, Set.of(State.CHOPPING),
                State.CHOPPING,  Set.of(State.SEARCHING));
    }

//? if 1.20.1 {
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.is(net.minecraftforge.common.Tags.Blocks.ORES); }
//?} else {
    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) { return s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES); }
//?}

    /** 状态机驱动 — 取消检查 + 表分发 (每次 tick 委托执行器, 阶段镜像同步) */
    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        if (com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.STATE_CANCELLED.equals(
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getState(maid))) return null;
        return HANDLERS.get(s).apply(new StateCtx(maid, world, maid.getPersistentData()));
    }

    /** v79.27: 终结路径汇聚 (cancel/complete/fail/timeout) — 清 FSM 状态 + 挖矿静态缓存 (maidId 泄漏/串扰) */
    @Override
    protected void cleanup(EntityMaid maid) {
        clearState(maid);
        ChainHarvestExecute.clearMaidState(maid);
    }

    /** SEARCHING — 委托扫描/导航; 蓄力队列出现 → CHOPPING */
    private static State handleSearching(StateCtx ctx) {
        ChainHarvestExecute.execute(ctx.world(), ctx.maid(), ctx.maid().blockPosition(),
                ctx.data(), ChainHarvestExecute.Mode.ORE);
        return ctx.data().contains(ChainHarvestExecute.KEY_QUEUE) ? State.CHOPPING : null;
    }

    /** CHOPPING — 委托蓄力/破坏; 队列清空 → SEARCHING */
    private static State handleChopping(StateCtx ctx) {
        ChainHarvestExecute.execute(ctx.world(), ctx.maid(), ctx.maid().blockPosition(),
                ctx.data(), ChainHarvestExecute.Mode.ORE);
        return ctx.data().contains(ChainHarvestExecute.KEY_QUEUE) ? null : State.SEARCHING;
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        ItemStack tool = maid.getMainHandItem();
        if (!ToolStateReader.isPickaxe(tool)) {
            return PipelineResult.failed("需要主手持镐才能挖矿");
        }
        if (!ToolJudge.isToolUsable(tool, 1)) {
            return PipelineResult.failed("镐耐久不足");
        }
        return PipelineResult.ok("开始连锁挖矿");
    }

    @Override
    public IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                return ChainHarvestExecute.execute(w, m, p, d, ChainHarvestExecute.Mode.ORE);
            }
        };
    }

    /** v67.8: 单女仆采集名单配置 (TLM 任务设置标签页) */
    @Override
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, "collect_ore");
    }

    @Override
    public List<TaskStep> steps() {
        return List.of(
                new TaskStep("search", "寻找矿石", StepType.COLLECT, List.of()),
                new TaskStep("mine", "连锁挖掘", StepType.INTERACT, List.of("search"))
        );
    }
}
