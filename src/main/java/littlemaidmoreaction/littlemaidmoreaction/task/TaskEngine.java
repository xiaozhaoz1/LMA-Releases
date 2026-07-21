package littlemaidmoreaction.littlemaidmoreaction.task;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import net.minecraft.nbt.CompoundTag;

/**
 * 任务引擎 (v49) — 超时看门狗 + adapter 标记轮询。
 *
 * <p>v49: 新增 adapter→task 解耦轮询。adapter 写 NBT 标记，TaskEngine 读标记并调 Dispatcher。
 * 唯一职责: 读取 NBT → 决策 → 委托 TaskDispatcher。
 */
public final class TaskEngine {

    static final int DEFAULT_TIMEOUT = 1200;

    private TaskEngine() {}

    public static void tick(RuleContext ctx) {
        CompoundTag data = ctx.maid().getPersistentData();

        // ── v49: 处理 adapter 标记 (adapter→task 解耦) ──

        // ① TLM 任务切换 → 取消 LMA 任务
        String tlmSwitch = data.getString(TaskKeys.TLM_SWITCH);
        if (!tlmSwitch.isEmpty()) {
            data.remove(TaskKeys.TLM_SWITCH);
            TaskDispatcher.cancel(ctx.maid());
            return;
        }

        // ② GUI 启动新任务 → 提交
        String guiInit = data.getString(TaskKeys.GUI_INIT);
        if (!guiInit.isEmpty()) {
            data.remove(TaskKeys.GUI_INIT);
            TaskDispatcher.submit(ctx.maid(), guiInit, null, 0);
            return;
        }

        // ③ adapter 写入 completed/failed → 委托 Dispatcher 清理
        String state = data.getString(TaskKeys.FLOW_STATE);
        if (TaskKeys.STATE_COMPLETED.equals(state)) {
            TaskDispatcher.complete(ctx.maid());
            return;
        }
        if (TaskKeys.STATE_FAILED.equals(state)) {
            String reason = data.getString(TaskKeys.FAIL_REASON);
            TaskDispatcher.fail(ctx.maid(), reason.isEmpty() ? "adapter" : reason);
            return;
        }

        // ── 超时看门狗 (v44) ──

        String task = data.getString(TaskKeys.FLOW_TASK);
        if (task.isEmpty()) return;

        if (!TaskKeys.STATE_IN_PROGRESS.equals(state)) return;

        long lastTick = data.getLong(TaskKeys.FLOW_TICK);
        long now = ctx.maid().level().getGameTime();
        if (lastTick == 0) return;

        int timeout = data.getInt(TaskKeys.FLOW_TIMEOUT);
        if (timeout <= 0) timeout = DEFAULT_TIMEOUT;

        if (lastTick > now) {
            long skew = lastTick - now;
            if (skew > 1_728_000L) {
                LittleMaidMoreAction.LOGGER.warn("[TaskEngine] task '{}' stale (skew={}), cleaning via Dispatcher", task, skew);
                TaskDispatcher.timeout(ctx.maid());
            }
            return;
        }

        if (now - lastTick > timeout) {
            LittleMaidMoreAction.LOGGER.info("[TaskEngine] task '{}' timed out ({}t > {}t)", task, now - lastTick, timeout);
            TaskDispatcher.timeout(ctx.maid());
        }
    }
}
