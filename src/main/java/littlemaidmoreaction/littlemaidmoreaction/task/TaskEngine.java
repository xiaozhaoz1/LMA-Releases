package littlemaidmoreaction.littlemaidmoreaction.task;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * v53: adapter 标记轮询 + isLongRunning 超时看门狗。
 *
 * <p>v53: 超时仅检查 isLongRunning()=true 的管线（Create 任务）。
 * Vanilla 一次性任务由 Brain ~100tick 循环自然驱动，完成/失败不终止任务。
 */
public final class TaskEngine {

    static final int DEFAULT_TIMEOUT = 1200;

    private TaskEngine() {}

    public static void tick(RuleContext ctx) {
        CompoundTag data = ctx.maid().getPersistentData();

        // ── v49: 处理 adapter 标记 ──

        // ① TLM 任务切换 → 取消 LMA 任务 + 若切换到LMA则立即提交
        String tlmSwitch = data.getString(TaskKeys.TLM_SWITCH);
        if (!tlmSwitch.isEmpty()) {
            data.remove(TaskKeys.TLM_SWITCH);
            TaskDispatcher.cancel(ctx.maid());
            // v56: 切换到LMA任务 → 立即提交新任务(消除Brain周期延迟)
            ResourceLocation uid = ResourceLocation.tryParse(tlmSwitch);
            if (uid != null && LittleMaidMoreAction.MOD_ID.equals(uid.getNamespace())) {
                String newType = LmaTaskTypeRegistry.extractTaskType(uid.getPath());
                if (newType != null && TaskRegistry.get(newType) != null) {
                    TaskDispatcher.submit(ctx.maid(), newType, null, 0);
                    return;
                }
            }
            return;
        }

        // ② GUI 启动新任务 → 提交
        String guiInit = data.getString(TaskKeys.GUI_INIT);
        if (!guiInit.isEmpty()) {
            data.remove(TaskKeys.GUI_INIT);
            TaskDispatcher.submit(ctx.maid(), guiInit, null, 0);
            return;
        }

        // ── 超时看门狗 (v53: 仅 isLongRunning) ──

        String task = data.getString(TaskKeys.FLOW_TASK);
        if (task.isEmpty()) return;

        if (!TaskKeys.STATE_IN_PROGRESS.equals(data.getString(TaskKeys.FLOW_STATE))) return;

        // v53: 仅持续任务 (Create) 检查超时
        var h = TaskRegistry.get(task);
        if (h == null || !h.pipeline().isLongRunning()) return;

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
