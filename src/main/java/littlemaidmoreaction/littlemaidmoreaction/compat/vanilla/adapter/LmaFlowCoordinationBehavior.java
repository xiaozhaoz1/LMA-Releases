package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import com.google.common.collect.ImmutableMap;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskRegistry;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.TaskToggle;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.TaskResult;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.VanillaConstants;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.BlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter.LmaTaskMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiPredicate;

/** v35: 状态机编排 — 任务分发委托 TaskRegistry */
public final class LmaFlowCoordinationBehavior extends MaidCheckRateTask {

    private static final int CHECK_INTERVAL = VanillaConstants.NAV_CHECK_INTERVAL;
    private static final int NAV_TIMEOUT_TICKS = VanillaConstants.NAV_TIMEOUT_TICKS;
    private static final double ARRIVE_DIST_SQR = VanillaConstants.ARRIVE_DIST_SQR;

    public LmaFlowCoordinationBehavior() {
        super(ImmutableMap.of());
        this.setMaxCheckRate(CHECK_INTERVAL);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, EntityMaid maid) {
        if (!super.checkExtraStartConditions(world, maid)) return false;

        String task = LmaFlowTask.getCurrentFlowTaskType(maid);
        if (!task.isEmpty() && !"none".equals(task)) {
            return "in_progress".equals(maid.getPersistentData().getString("lma_flow_state"));
        }

        var maidTask = maid.getTask();
        if (!LmaFlowTask.isLmaTask(maidTask)) return false;
        String taskType = LmaTaskTypeRegistry.extractTaskType(maidTask.getUid().getPath());
        if (taskType == null) return false;
        // ★ v35.2: 任务开关门控
        if (!TaskToggle.isEnabled(taskType)) return false;

        CompoundTag data = maid.getPersistentData();
        data.putString("lma_flow_task", taskType);
        data.putString("lma_flow_state", "in_progress");
        data.putLong("lma_flow_tick", world.getGameTime());
        LittleMaidMoreAction.LOGGER.debug("[V29.1] GUI-init task '{}'", taskType);
        return true;
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long gameTime) {
        CompoundTag data = maid.getPersistentData();
        String taskType = data.getString("lma_flow_task");
        if (taskType.isEmpty()) return;

        var handler = TaskRegistry.get(taskType);
        if (handler == null) { failTask(maid, "未知任务类型: " + taskType); return; }

        BlockPos navTarget = LmaTaskMemory.getNavTarget(maid);
        if (navTarget != null) {
            if (gameTime - LmaTaskMemory.getNavStartTick(maid) > NAV_TIMEOUT_TICKS) {
                LmaTaskMemory.clearAllNav(maid);
            } else if (isBlockValid(world, navTarget, handler)) {
                if (navTarget.distSqr(maid.blockPosition()) < ARRIVE_DIST_SQR) {
                    LmaTaskMemory.clearAllNav(maid);
                    maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    doExecute(world, maid, navTarget, handler);
                    return;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(maid, navTarget, 1.0F, 2);
                return;
            } else {
                LmaTaskMemory.clearAllNav(maid);
            }
        }

        BlockPos nearest = searchBlock(world, maid, handler);
        if (nearest == null) {
            failTask(maid, "找不到" + taskType + "方块");
            return;
        }
        if (nearest.distSqr(maid.blockPosition()) < ARRIVE_DIST_SQR) {
            doExecute(world, maid, nearest, handler);
            return;
        }
        LmaTaskMemory.setNavTarget(maid, nearest);
        LmaTaskMemory.setNavStartTick(maid, gameTime);
        BehaviorUtils.setWalkAndLookTargetMemories(maid, nearest, 1.0F, 2);
    }

    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        BlockPos navTarget = LmaTaskMemory.getNavTarget(maid);
        if (navTarget == null) return;
        if (navTarget.distSqr(maid.blockPosition()) < ARRIVE_DIST_SQR) {
            if ("in_progress".equals(maid.getPersistentData().getString("lma_flow_state"))) {
                String taskType = maid.getPersistentData().getString("lma_flow_task");
                var handler = TaskRegistry.get(taskType);
                if (handler == null) return;
                LmaTaskMemory.clearAllNav(maid);
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                doExecute(world, maid, navTarget, handler);
            }
        }
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long gameTime) {
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    // -- private helpers --

    private void doExecute(ServerLevel world, EntityMaid maid, BlockPos pos,
                           TaskRegistry.TaskHandler handler) {
        TaskResult result = handler.executor().execute(world, maid, pos, maid.getPersistentData());
        switch (result) {
            case SUCCESS -> completeTask(maid);
            case FAILED -> failTask(maid, handler.taskType() + " 执行失败");
            case CONTINUE -> { /* 任务继续 */ }
        }
    }

    /** 搜索目标方块: targetBlock!=null→BlockState匹配, null→BlockEntity匹配 */
    private static BlockPos searchBlock(ServerLevel world, EntityMaid maid,
                                         TaskRegistry.TaskHandler handler) {
        BiPredicate<BlockPos, BlockState> predicate;
        if (handler.targetBlock() != null) {
            predicate = (p, s) -> s.is(handler.targetBlock());
        } else {
            predicate = switch (handler.taskType()) {
                case "furnace" -> (p, s) -> world.getBlockEntity(p) instanceof AbstractFurnaceBlockEntity;
                case "altar_craft" -> (p, s) -> world.getBlockEntity(p) instanceof TileEntityAltar;
                default -> null;
            };
            if (predicate == null) return null;
        }
        var matches = BlockSearch.findBlocksInRange(world, maid.blockPosition(),
            maid.getRestrictRadius(), predicate);
        return matches.isEmpty() ? null : matches.get(0).pos();
    }

    private static boolean isBlockValid(ServerLevel world, BlockPos pos,
                                         TaskRegistry.TaskHandler handler) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        if (handler.targetBlock() != null) return handler.isValid().test(state);
        // targetBlock==null → BlockEntity 验证
        return switch (handler.taskType()) {
            case "furnace" -> world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity;
            case "altar_craft" -> world.getBlockEntity(pos) instanceof TileEntityAltar;
            default -> false;
        };
    }

    static void completeTask(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        data.putString("lma_flow_state", "completed");
        data.putString("lma_task_completed", data.getString("lma_flow_task"));
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        data.remove("lma_flow_cached");
    }

    static void failTask(EntityMaid maid, String reason) {
        CompoundTag data = maid.getPersistentData();
        data.putString("lma_flow_state", "failed");
        data.putLong("lma_flow_tick", maid.level().getGameTime());
        data.remove("lma_flow_cached");
        data.putString("lma_fail_reason", reason);
        LittleMaidMoreAction.LOGGER.warn("[V29.1] task '{}' failed: {}", data.getString("lma_flow_task"), reason);
    }

    @Deprecated
    static void clearNavData(CompoundTag data) {
        data.remove("lma_nav_tx"); data.remove("lma_nav_ty"); data.remove("lma_nav_tz");
        data.remove("lma_nav_block"); data.remove("lma_nav_tick");
    }
}
