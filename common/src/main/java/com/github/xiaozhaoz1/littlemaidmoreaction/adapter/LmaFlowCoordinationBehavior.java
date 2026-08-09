package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.TaskResult;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.VanillaConstants;
import com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi;
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
 *
 * <p>工作站任务 (furnace/jukebox/bell_ring/craft_chain):
 * <ul>
 *   <li>start → searchForDestination → WALK_TARGET → 到达</li>
 *   <li>tick → 到达后每30tick执行一次, TARGET_POS保留</li>
 *   <li>目标失效(方块被破坏) → 自动重搜索</li>
 *   <li>一次性任务 SUCCESS → complete → 清理 → 恢复 idle</li>
 * </ul>
 */
public final class LmaFlowCoordinationBehavior extends MaidMoveToBlockTask {

    private static final double ARRIVE_DIST_SQR = VanillaConstants.ARRIVE_DIST_SQR;
    /** v64: 工作站连续执行冷却 (tick) */
    private static final int EXECUTE_INTERVAL = 30;
    /** v64: 上次 doExecute 的 gameTime */
    private long lastExecuteTick;

    public LmaFlowCoordinationBehavior() {
        super(1.0F, 4);
        setMaxCheckRate(VanillaConstants.NAV_CHECK_INTERVAL);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, EntityMaid maid) {
        if (!super.checkExtraStartConditions(world, maid)) return false;

        String task = FlowTaskData.getTask(maid);
        if (!task.isEmpty() && !"none".equals(task)) {
            if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(TaskKeys.FLOW_STATE))) {
                var curTask = maid.getTask();
                if (LmaFlowTask.isLmaTask(curTask)) {
                    String curType = LmaTaskTypeRegistry.extractTaskType(curTask.getUid().getPath());
                    if (curType != null && !curType.equals(task) && TaskRegistry.get(curType) != null) {
                        maid.getPersistentData().putString(TaskKeys.TLM_SWITCH, curType);
                    }
                }
                return true;
            }
            String flowState = maid.getPersistentData().getString(TaskKeys.FLOW_STATE);
            if (!flowState.isEmpty() && !TaskKeys.STATE_IN_PROGRESS.equals(flowState)) {
                return false;
            }
        }

        // v53: FLOW_TASK 为空 + TLM task 是 LMA + 非 CANCELLED → 自动启动
        var maidTask = maid.getTask();
        if (LmaFlowTask.isLmaTask(maidTask)) {
            if (TaskKeys.STATE_CANCELLED.equals(maid.getPersistentData().getString(TaskKeys.FLOW_STATE))) {
                return false;
            }
            String taskType = LmaTaskTypeRegistry.extractTaskType(maidTask.getUid().getPath());
            if (taskType != null && TaskRegistry.get(taskType) != null && TaskToggle.isEnabled(taskType)) {
                maid.getPersistentData().putString(TaskKeys.GUI_INIT, taskType);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, EntityMaid maid, long gameTime) {
        return TaskKeys.STATE_IN_PROGRESS.equals(
            maid.getPersistentData().getString(TaskKeys.FLOW_STATE));
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

        if (!maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get())) {
            if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(TaskKeys.FLOW_STATE))) {
                LittleMaidMoreAction.LOGGER.info("[LMA/Brain] no target found for {}, execute at current pos", taskType);
                doExecute(world, maid, maid.blockPosition(), handler);
            }
        } else {
            LittleMaidMoreAction.LOGGER.info("[LMA/Brain] navigating to target for {}", taskType);
        }
    }

    /**
     * v64: 持续循环 — 不擦TARGET_POS, 锚定工作站, 冷却节流。
     * 目标失效时自动重搜索。
     */
    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        var mem = maid.getBrain().getMemory(InitEntities.TARGET_POS.get());
        if (mem.isEmpty()) return;

        BlockPos target = mem.get().currentBlockPosition();

        // 未到达 → 导航中: 心跳防超时误杀 (v67.3, 慢导航/绕路 >60s 不被看门狗当卡死)
        if (target.distSqr(maid.blockPosition()) >= ARRIVE_DIST_SQR) {
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateManager.heartbeat(maid, gameTime);
            return;
        }

        // 冷却
        if (gameTime - lastExecuteTick < EXECUTE_INTERVAL) return;

        // 目标失效(方块被破坏/替换) → 重搜索
        if (!shouldMoveTo(world, maid, target)) {
            maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            searchForDestination(world, maid);
            return;
        }

        lastExecuteTick = gameTime;

        if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(TaskKeys.FLOW_STATE))) {
            var handler = TaskRegistry.get(FlowTaskData.getTask(maid));
            if (handler != null) doExecute(world, maid, target, handler);
        }
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long gameTime) {
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private void doExecute(ServerLevel world, EntityMaid maid, BlockPos pos,
                           TaskRegistry.TaskHandler handler) {
        // v79.27: 双驱动修复 — needsGameTick=true 管线由 GameTickPipelineManager 独占驱动
        // (每 tick), Brain 侧每 30t 重复执行真实逻辑 (扫描/寻路/开脉) = 冗余 + 双份日志。
        // furnace/craft_chain/jukebox/bell_ring (needsGameTick=false) 保持 Brain 驱动 + SUCCESS 计数。
        if (handler.pipeline().needsGameTick()) {
            return;
        }
        if (handler.pipeline().isLongRunning()) {
            com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskStateManager.heartbeat(
                maid, world.getGameTime());
        }
        TaskResult result = handler.executor().execute(world, maid, pos, maid.getPersistentData());
        LittleMaidMoreAction.LOGGER.info("[LMA/Brain] execute task={} result={} pos={}", handler.taskType(), result, pos.toShortString());
        switch (result) {
            case SUCCESS -> {
                var data = maid.getPersistentData();
                int counter = data.getInt(TaskKeys.FLOW_COUNTER) + 1;
                data.putInt(TaskKeys.FLOW_COUNTER, counter);
                int max = data.getInt(TaskKeys.FLOW_MAX_COUNT);
                LittleMaidMoreAction.LOGGER.info("[LMA/Brain] task={} SUCCESS counter={}/{}", handler.taskType(), counter, max > 0 ? max : -1);
                if (max > 0 && counter >= max) {
                    completeTask(maid);
                    return;
                }
                // v63.3: 一次性任务(max=0且非长运行) → 首次SUCCESS即完成
                if (max == 0 && !handler.pipeline().isLongRunning()) {
                    completeTask(maid);
                    return;
                }
            }
            case FAILED -> {
                // v79.21: 统一失败气泡 (红色 ✘ + 600t 节流)
                MaidChatBubbleApi.showFail(maid, handler.taskType() + " 失败");
                LittleMaidMoreAction.LOGGER.warn("[LMA/Brain] task '{}' execute FAILED", handler.taskType());
            }
            case CONTINUE -> { /* 持续执行 */ }
        }
    }

    /**
     * v64: 任务完成 — 清理导航记忆, 恢复idle.
     * v67.3: 统一走 TaskDispatcher.complete (onCleanup + STATE_COMPLETED + clearAll) —
     * 修复绕过 Dispatcher 导致 FLOW_TASK/COUNTER/MAX_COUNT 残留 (跨任务累积误判)。
     */
    private void completeTask(EntityMaid maid) {
        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.setTask(TaskManager.getIdleTask());
        com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher.complete(maid);
    }
}
