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
 * 连锁砍树管道 (v36) — 校验主手斧可用。
 * 实际搜索/导航/破坏由 ChainHarvestExecute 执行。
 *
 * <p>v79.7 状态机化: SEARCHING/CHOPPING 双态 — 阶段由执行数据镜像
 * (lma_chain_queue 存在 = 蓄力破坏中), 状态为装饰性同步, 行为零变化。
 */
public final class ChainWoodPipeline extends TaskStateMachine<ChainWoodPipeline.State> {

    enum State { SEARCHING, CHOPPING }

    /** 状态处理器表 — 每状态独立方法 (v79.7 示范模式) */
    private static final Map<State, Function<StateCtx, State>> HANDLERS = Map.of(
            State.SEARCHING, ChainWoodPipeline::handleSearching,
            State.CHOPPING,  ChainWoodPipeline::handleChopping);

    private record StateCtx(EntityMaid maid, ServerLevel world, CompoundTag data) {}

    @Override protected Class<State> stateClass() { return State.class; }
    @Override protected State initialState() { return State.SEARCHING; }
    @Override public String taskType() { return "collect_wood"; }
    @Override public boolean needsGameTick() { return true; }
    @Override public boolean isLongRunning() { return true; }

    @Override
    protected Map<State, Set<State>> transitions() {
        return Map.of(
                State.SEARCHING, Set.of(State.CHOPPING),
                State.CHOPPING,  Set.of(State.SEARCHING));
    }

    @Override public boolean isTargetBlock(ServerLevel w, BlockPos p, BlockState s, EntityMaid m) {
        return s.is(net.minecraft.tags.BlockTags.LOGS);
    }

    /** 状态机驱动 — 取消检查 + 表分发 (每次 tick 委托执行器, 阶段镜像同步) */
    @Override
    protected State tick(State s, ServerLevel world, EntityMaid maid) {
        if (com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.STATE_CANCELLED.equals(
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getState(maid))) return null;
        return HANDLERS.get(s).apply(new StateCtx(maid, world, maid.getPersistentData()));
    }

    /** SEARCHING — 委托扫描/导航; 蓄力队列出现 → CHOPPING */
    private static State handleSearching(StateCtx ctx) {
        ChainHarvestExecute.execute(ctx.world(), ctx.maid(), ctx.maid().blockPosition(),
                ctx.data(), ChainHarvestExecute.Mode.WOOD);
        return ctx.data().contains(ChainHarvestExecute.KEY_QUEUE) ? State.CHOPPING : null;
    }

    /** CHOPPING — 委托蓄力/破坏; 队列清空 → SEARCHING */
    private static State handleChopping(StateCtx ctx) {
        ChainHarvestExecute.execute(ctx.world(), ctx.maid(), ctx.maid().blockPosition(),
                ctx.data(), ChainHarvestExecute.Mode.WOOD);
        return ctx.data().contains(ChainHarvestExecute.KEY_QUEUE) ? null : State.SEARCHING;
    }

    /** v79.27: 终结路径汇聚 (cancel/complete/fail/timeout) — 清 FSM 状态 + 挖矿静态缓存 (maidId 泄漏/串扰) */
    @Override
    protected void cleanup(EntityMaid maid) {
        clearState(maid);
        ChainHarvestExecute.clearMaidState(maid);
    }

    @Override
    public PipelineResult validate(ServerLevel level, EntityMaid maid, PipelineContext ctx) {
        ItemStack tool = maid.getMainHandItem();
        // v36.1: 无斧不拦截 — 慢砍模式（斧影响速度而非可行性）
        if (!ToolStateReader.isAxe(tool)) {
            return PipelineResult.ok("无斧慢砍模式（持斧砍伐更快且更耐用）");
        }
        if (!ToolJudge.isToolUsable(tool, 1)) {
            return PipelineResult.ok("斧即将损坏，将以慢砍模式作业");
        }
        return PipelineResult.ok("开始连锁砍树");
    }

    @Override
    public IExecutor executor() {
        return new IExecutor() {
            @Override public TaskResult execute(ServerLevel w, EntityMaid m, BlockPos p, CompoundTag d) {
                return ChainHarvestExecute.execute(w, m, p, d, ChainHarvestExecute.Mode.WOOD);
            }
        };
    }

    /** v67.8: 单女仆采集名单配置 (TLM 任务设置标签页) */
    @Override
    public net.minecraft.world.MenuProvider getConfigGuiProvider(EntityMaid maid) {
        return TaskConfigGuiFactory.itemListConfig(maid, "collect_wood");
    }

    @Override
    public List<TaskStep> steps() {
        return List.of(
                new TaskStep("search", "寻找树木", StepType.COLLECT, List.of()),
                new TaskStep("chop", "连锁砍伐", StepType.INTERACT, List.of("search"))
        );
    }
}
