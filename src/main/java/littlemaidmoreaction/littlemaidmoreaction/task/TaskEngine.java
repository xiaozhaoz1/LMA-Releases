package littlemaidmoreaction.littlemaidmoreaction.task;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaFlowTask;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskProgressDisplay;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.RuleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 任务引擎 — 内置 tick 处理 (v35.1: TaskKeys 常量替代硬编码)。
 */
public final class TaskEngine {

    static final int DEFAULT_TIMEOUT = 1200;

    private TaskEngine() {}

    public static void tick(RuleContext ctx) {
        CompoundTag data = ctx.maid().getPersistentData();
        String task = data.getString(TaskKeys.FLOW_TASK);
        if (task.isEmpty()) return;

        String state = data.getString(TaskKeys.FLOW_STATE);
        int step = data.getInt(TaskKeys.FLOW_STEP);
        long lastTick = data.getLong(TaskKeys.FLOW_TICK);
        long now = ctx.maid().level().getGameTime();

        // ── 0. 任务首次激活时保存状态 ──
        if (TaskKeys.STATE_IN_PROGRESS.equals(state) && !data.contains(TaskKeys.SAVED_HOME)) {
            var maid = ctx.maid();
            data.putBoolean(TaskKeys.SAVED_HOME, maid.isHomeModeEnable());
            data.putString(TaskKeys.SAVED_PICKUP, maid.getConfigManager().getPickupType().name());
            BlockPos pos = maid.blockPosition();
            data.putLong(TaskKeys.SAVED_HOME_POS, ((long) pos.getX() << 32) | (pos.getZ() & 0xFFFFFFFFL));
            LittleMaidMoreAction.LOGGER.debug("[TaskEngine] saved state for '{}': home={} pickup={}",
                task, data.getBoolean(TaskKeys.SAVED_HOME), data.getString(TaskKeys.SAVED_PICKUP));
        }

        // ── 1. 状态变化检测 ──
        String currentSig = task + "|" + data.getString(TaskKeys.FLOW_TASK_ID) + "|" + state + "|" + step;
        String cachedSig = data.getString(TaskKeys.FLOW_CACHED);
        if (!currentSig.equals(cachedSig)) {
            data.putString(TaskKeys.FLOW_CACHED, currentSig);
            return;
        }

        // ── 2. 完成/失败/停止 → count 检查 ──
        if (TaskKeys.STATE_COMPLETED.equals(state) || TaskKeys.STATE_FAILED.equals(state) || TaskKeys.STATE_STOPPED.equals(state)) {
            int counter = data.getInt(TaskKeys.FLOW_COUNTER);
            int maxCount = data.getInt(TaskKeys.FLOW_MAX_COUNT);

            if (maxCount > 0 && counter + 1 >= maxCount) {
                restoreState(ctx.maid(), data);
                data.putString(TaskKeys.TASK_COMPLETED, task);
                LmaTaskProgressDisplay.showComplete(ctx.maid(), task, counter, maxCount);
                data.remove(TaskKeys.FLOW_TASK);
                data.remove(TaskKeys.FLOW_TASK_ID);
                data.remove(TaskKeys.FLOW_STATE);
                data.remove(TaskKeys.FLOW_STEP);
                data.remove(TaskKeys.FLOW_COUNTER);
                data.remove(TaskKeys.FLOW_MAX_COUNT);
                data.remove(TaskKeys.FLOW_TICK);
                data.remove(TaskKeys.FLOW_TIMEOUT);
                data.remove(TaskKeys.FLOW_DATA);
                data.remove(TaskKeys.FLOW_CACHED);
                LittleMaidMoreAction.LOGGER.info("[TaskEngine] task '{}' stopped after {} runs", task, counter);
            } else {
                counter++;
                data.putInt(TaskKeys.FLOW_COUNTER, counter);
                data.putString(TaskKeys.FLOW_STATE, TaskKeys.STATE_IN_PROGRESS);
                data.putInt(TaskKeys.FLOW_STEP, 0);
                data.putLong(TaskKeys.FLOW_TICK, now);
                data.remove(TaskKeys.FLOW_CACHED);
                LittleMaidMoreAction.LOGGER.info("[TaskEngine] task '{}' auto-restart (run {}/{})",
                    task, counter, maxCount > 0 ? String.valueOf(maxCount) : "∞");
            }
            return;
        }

        // ── 3. 超时检测 ──
        if (!TaskKeys.STATE_IN_PROGRESS.equals(state)) return;
        int timeout = data.getInt(TaskKeys.FLOW_TIMEOUT);
        if (timeout <= 0) timeout = DEFAULT_TIMEOUT;
        if (lastTick == 0) return;
        if (lastTick > now) {
            long skew = lastTick - now;
            if (skew > 1_728_000L) {
                LittleMaidMoreAction.LOGGER.warn("[TaskEngine] task '{}' stale (lastTick={} >> now={}, skew={}), cleaning",
                    task, lastTick, now, skew);
                restoreState(ctx.maid(), data);
                data.remove(TaskKeys.FLOW_TASK); data.remove(TaskKeys.FLOW_TASK_ID);
                data.remove(TaskKeys.FLOW_STATE); data.remove(TaskKeys.FLOW_STEP);
                data.remove(TaskKeys.FLOW_COUNTER); data.remove(TaskKeys.FLOW_MAX_COUNT);
                data.remove(TaskKeys.FLOW_TICK); data.remove(TaskKeys.FLOW_TIMEOUT);
                data.remove(TaskKeys.FLOW_DATA); data.remove(TaskKeys.FLOW_CACHED);
            }
            return;
        }
        if (now - lastTick > timeout) {
            data.putString(TaskKeys.FLOW_STATE, TaskKeys.STATE_FAILED);
            data.putLong(TaskKeys.FLOW_TICK, now);
            data.putString(TaskKeys.FLOW_CACHED, task + "|" + data.getString(TaskKeys.FLOW_TASK_ID) + "|failed|" + step);
            LittleMaidMoreAction.LOGGER.info("[TaskEngine] task '{}' timed out", task);
        }
    }

    private static void restoreState(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid,
                                      CompoundTag data) {
        if (data.contains(TaskKeys.SAVED_HOME)) {
            boolean wasHome = data.getBoolean(TaskKeys.SAVED_HOME);
            maid.setHomeModeEnable(wasHome);
            data.remove(TaskKeys.SAVED_HOME);
        }
        if (data.contains(TaskKeys.SAVED_PICKUP)) {
            String wasPickup = data.getString(TaskKeys.SAVED_PICKUP);
            try {
                var type = com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType.valueOf(wasPickup);
                maid.getConfigManager().setPickupType(type);
            } catch (IllegalArgumentException ignored) {}
            data.remove(TaskKeys.SAVED_PICKUP);
        }
        data.remove(TaskKeys.SAVED_HOME_POS);
        LittleMaidMoreAction.LOGGER.debug("[TaskEngine] restored state after task");
        LmaFlowTask.restorePreviousTask(maid);
    }
}
