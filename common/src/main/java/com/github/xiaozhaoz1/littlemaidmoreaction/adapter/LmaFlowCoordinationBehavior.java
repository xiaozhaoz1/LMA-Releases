package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * v53: Brain 导航 + 循环执行。
 * v64: tick 持续循环 — 不擦TARGET_POS, 工作站锚定, 冷却节流。
 * v79.45: 纯导航化 — 执行/计数/完成全迁 GameTickPipelineManager (WorkStationPipeline 基类 tick),
 *   本行为只负责: 导航 (searchForDestination/shouldMoveTo) + 切换检测 (checkExtraStartConditions) +
 *   目标失效重搜。心跳归 GMPM (每 20t, isLongRunning 分支)。
 *
 * <p>工作站任务 (furnace/jukebox/bell_ring/craft_chain):
 * <ul>
 *   <li>start → searchForDestination → WALK_TARGET → 到达</li>
 *   <li>GMPM tick → WorkStationPipeline.tick 节拍执行, TARGET_POS 保留</li>
 *   <li>目标失效(方块被破坏) → 本行为 tick 重搜索 (erase 后 Brain 不自动重搜)</li>
 *   <li>完成判定在管线基类 (SUCCESS 计数链 → TaskDispatcher.complete)</li>
 * </ul>
 */
public final class LmaFlowCoordinationBehavior extends MaidMoveToBlockTask {

    private static final double ARRIVE_DIST_SQR = VanillaConstants.ARRIVE_DIST_SQR;

    public LmaFlowCoordinationBehavior() {
        super(1.0F, 4);
        setMaxCheckRate(VanillaConstants.NAV_CHECK_INTERVAL);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, EntityMaid maid) {
        if (!super.checkExtraStartConditions(world, maid)) return false;

        String task = FlowTaskData.getTask(maid);
        if (!task.isEmpty() && !"none".equals(task)) {
            if (TaskKeys.STATE_IN_PROGRESS.equals(FlowTaskData.getState(maid))) {
                var curTask = maid.getTask();
                if (LmaFlowTask.isLmaTask(curTask)) {
                    String curType = LmaTaskTypeRegistry.extractTaskType(curTask.getUid().getPath());
                    if (curType != null && !curType.equals(task) && TaskRegistry.get(curType) != null) {
                        // 值格式契约: 消费方 GameTickPipelineManager.tickActive 用 ResourceLocation.tryParse
                        // 期望完整 uid (如 "lma:task/craft_chain"); 裸 taskType 无 ":" 解析失败 → 误 cancel (错题 #179)
                        TaskMetaData.setTlmSwitch(maid, curTask.getUid().toString());
                    }
                }
                return true;
            }
            String flowState = FlowTaskData.getState(maid);
            if (!flowState.isEmpty() && !TaskKeys.STATE_IN_PROGRESS.equals(flowState)) {
                return false;
            }
        }

        // FLOW_TASK 为空 + TLM task 是 LMA + 非 CANCELLED → 自动启动
        var maidTask = maid.getTask();
        if (LmaFlowTask.isLmaTask(maidTask)) {
            if (TaskKeys.STATE_CANCELLED.equals(FlowTaskData.getState(maid))) {
                return false;
            }
            String taskType = LmaTaskTypeRegistry.extractTaskType(maidTask.getUid().getPath());
            if (taskType != null && TaskRegistry.get(taskType) != null && TaskToggle.isEnabled(taskType)) {
                TaskMetaData.setGuiInit(maid, taskType);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, EntityMaid maid, long gameTime) {
        return TaskKeys.STATE_IN_PROGRESS.equals(
            FlowTaskData.getState(maid));
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel world, EntityMaid maid, BlockPos pos) {
        var handler = TaskRegistry.get(FlowTaskData.getTask(maid));
        if (handler == null) return false;
        return handler.pipeline().isTargetBlock(world, pos, world.getBlockState(pos), maid);
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long gameTime) {
        String taskType = FlowTaskData.getTask(maid);
        if (taskType.isEmpty()) return;

        var handler = TaskRegistry.get(taskType);
        if (handler == null) return;

        LittleMaidMoreAction.LOGGER.info("[LMA/Brain] start task={} at {}", taskType, maid.blockPosition().toShortString());
        searchForDestination(world, maid);
        if (maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get())) {
            LittleMaidMoreAction.LOGGER.info("[LMA/Brain] navigating to target for {}", taskType);
        } else {
            // 无目标不再就地执行 (执行归 GMPM/管线 tick) — 目标失效场景由 tick 重搜兜底
            LittleMaidMoreAction.LOGGER.info("[LMA/Brain] no target found for {}, awaiting GMPM tick", taskType);
        }
    }

    /**
     * 纯导航 tick — 目标失效重搜 (erase 后 Brain 不自动重搜 — canStillUse 恒 true)。
     * 心跳/节拍/执行全删 (归 GMPM + WorkStationPipeline.tick)。
     */
    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        var mem = maid.getBrain().getMemory(InitEntities.TARGET_POS.get());
        if (mem.isEmpty()) return;

        BlockPos target = mem.get().currentBlockPosition();

        // 目标失效(方块被破坏/替换) → 重搜索
        if (!shouldMoveTo(world, maid, target)) {
            maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            searchForDestination(world, maid);
        }
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long gameTime) {
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }
}
