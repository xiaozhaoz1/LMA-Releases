package littlemaidmoreaction.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidMoveToBlockTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
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
 * v53: Brain 导航 + 循环执行。
 *
 * <p>每 ~100tick 执行一轮:
 * <ul>
 *   <li>SUCCESS → counter++ → 达到 max_count? → 切 idle : 继续</li>
 *   <li>FAILED  → 气泡 → 继续</li>
 *   <li>CONTINUE → heartbeat → 继续 (Create)</li>
 * </ul>
 * <p>FLOW_TASK 为空 + TLM task 是 LMA → 自动 GUI_INIT 恢复。
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
        if (handler == null) return;

        searchForDestination(world, maid);

        if (!maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get())) {
            if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(TaskKeys.FLOW_STATE))) {
                doExecute(world, maid, maid.blockPosition(), handler);
            }
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
            case SUCCESS -> {
                var data = maid.getPersistentData();
                int counter = data.getInt(TaskKeys.FLOW_COUNTER) + 1;
                data.putInt(TaskKeys.FLOW_COUNTER, counter);
                int max = data.getInt(TaskKeys.FLOW_MAX_COUNT);
                if (max > 0 && counter >= max) {
                    maid.getChatBubbleManager().addTextChatBubble(
                        "✅ " + handler.taskType() + " 完成 (" + counter + "/" + max + ")");
                    maid.setTask(TaskManager.getIdleTask());
                    LmaTaskDataHelper.setFlowState(maid, TaskKeys.STATE_COMPLETED);
                    return;
                }
                String progress = max > 0 ? " (" + counter + "/" + max + ")" : "";
                maid.getChatBubbleManager().addTextChatBubble(
                    "✔ " + handler.taskType() + " 完成" + progress);
            }
            case FAILED -> {
                maid.getChatBubbleManager().addTextChatBubble(
                    "✘ " + handler.taskType() + " 失败");
                LittleMaidMoreAction.LOGGER.debug("[LMA] task '{}' execute failed", handler.taskType());
            }
            case CONTINUE -> { /* 持续执行 */ }
        }
    }
}
