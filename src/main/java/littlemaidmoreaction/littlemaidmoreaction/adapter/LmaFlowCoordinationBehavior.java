package littlemaidmoreaction.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.api.VanillaConstants;
import littlemaidmoreaction.littlemaidmoreaction.task.LmaTaskDataHelper;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskToggle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * v52: Brain 导航 — shouldMoveTo 委托 Pipeline.isTargetBlock()。
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

        String task = LmaTaskDataHelper.getFlowTask(maid);
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
            if (TaskKeys.STATE_COMPLETED.equals(flowState) || TaskKeys.STATE_FAILED.equals(flowState)) {
                return false;
            }
            var curTask = maid.getTask();
            String curType = LmaFlowTask.isLmaTask(curTask)
                    ? LmaTaskTypeRegistry.extractTaskType(curTask.getUid().getPath()) : null;
            if (curType == null || TaskRegistry.get(curType) == null) {
                return false;
            }
        }

        var maidTask = maid.getTask();
        if (!LmaFlowTask.isLmaTask(maidTask)) return false;
        String taskType = LmaTaskTypeRegistry.extractTaskType(maidTask.getUid().getPath());
        if (taskType == null) return false;
        if (!TaskToggle.isEnabled(taskType)) return false;

        maid.getPersistentData().putString(TaskKeys.GUI_INIT, taskType);
        LittleMaidMoreAction.LOGGER.debug("[V52] GUI-init task '{}'", taskType);
        return true;
    }

    @Override
    protected boolean shouldMoveTo(ServerLevel world, EntityMaid maid, BlockPos pos) {
        var handler = TaskRegistry.get(LmaTaskDataHelper.getFlowTask(maid));
        if (handler == null) return false;
        return handler.pipeline().isTargetBlock(world, pos, world.getBlockState(pos));
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long gameTime) {
        String taskType = LmaTaskDataHelper.getFlowTask(maid);
        if (taskType.isEmpty()) return;

        var handler = TaskRegistry.get(taskType);
        if (handler == null) { failTask(maid, "未知任务类型: " + taskType); return; }

        searchForDestination(world, maid);

        if (!maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get())) {
            // v52: 无目标=原地执行, 有目标但没找到=也原地执行 (idle scan)
            doExecute(world, maid, maid.blockPosition(), handler);
        }
    }

    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        var mem = maid.getBrain().getMemory(InitEntities.TARGET_POS.get());
        if (mem.isEmpty()) return;

        BlockPos target = mem.get().currentBlockPosition();
        if (target.distSqr(maid.blockPosition()) >= ARRIVE_DIST_SQR) return;

        maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(TaskKeys.FLOW_STATE))) {
            var handler = TaskRegistry.get(LmaTaskDataHelper.getFlowTask(maid));
            if (handler != null) doExecute(world, maid, target, handler);
        }
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long gameTime) {
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private void doExecute(ServerLevel world, EntityMaid maid, BlockPos pos,
                           TaskRegistry.TaskHandler handler) {
        if (handler.pipeline().isLongRunning()) {
            littlemaidmoreaction.littlemaidmoreaction.task.TaskStateManager.heartbeat(
                maid, world.getGameTime());
        }
        TaskResult result = handler.executor().execute(world, maid, pos, maid.getPersistentData());
        switch (result) {
            case SUCCESS -> completeTask(maid);
            case FAILED -> failTask(maid, handler.taskType() + " 执行失败");
            case CONTINUE -> { /* 任务继续 */ }
        }
    }

    static void completeTask(EntityMaid maid) {
        LmaTaskDataHelper.setFlowState(maid, TaskKeys.STATE_COMPLETED);
    }

    static void failTask(EntityMaid maid, String reason) {
        maid.getPersistentData().putString(TaskKeys.FAIL_REASON, reason);
        LmaTaskDataHelper.setFlowState(maid, TaskKeys.STATE_FAILED);
        LittleMaidMoreAction.LOGGER.warn("[V52] task '{}' failed: {}",
            LmaTaskDataHelper.getFlowTask(maid), reason);
    }
}
