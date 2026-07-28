package littlemaidmoreaction.littlemaidmoreaction.task.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaTaskTypeRegistry;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskRegistry;
import littlemaidmoreaction.littlemaidmoreaction.task.data.FlowTaskData;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskKeys;
import littlemaidmoreaction.littlemaidmoreaction.task.data.TaskToggle;
import littlemaidmoreaction.littlemaidmoreaction.task.sense.EnvSenseBroadcaster;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * v53: 通用 game-tick 驱动.
 * v61: 新增被动任务 tick (与主动任务并行).
 * v63: 新增 EnvSense 全局广播 (200tick 节流).
 * v64: 迁移 TaskEngine — TLM_SWITCH/GUI_INIT/超时看门狗 (每tick处理).
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class TaskTickHandler {

    private static final int DEFAULT_TIMEOUT = 1200;
    /** v63: 上次广播 tick (全局节流) */
    private static long nextBroadcastTick = 0;

    private TaskTickHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel sl : event.getServer().getAllLevels()) {
            long now = sl.getGameTime();
            for (var e : sl.getAllEntities()) {
                if (!(e instanceof EntityMaid maid)) continue;

                var data = maid.getPersistentData();
                String state = FlowTaskData.getState(maid);
                String task = FlowTaskData.getTask(maid);

                // ── v64: TLM_SWITCH/GUI_INIT 每 tick 处理 (从 TaskEngine 迁入) ──
                String tlmSwitch = data.getString(TaskKeys.TLM_SWITCH);
                if (!tlmSwitch.isEmpty()) {
                    data.remove(TaskKeys.TLM_SWITCH);
                    // 清除 GUI_INIT 防双 submit (v63.2)
                    data.remove(TaskKeys.GUI_INIT);
                    ResourceLocation uid = ResourceLocation.tryParse(tlmSwitch);
                    if (uid != null && LittleMaidMoreAction.MOD_ID.equals(uid.getNamespace())) {
                        String newType = LmaTaskTypeRegistry.extractTaskType(uid.getPath());
                        // v64: 如果FLOW_TASK已匹配且运行中, 跳过 (AI已通过StartTaskTool提交)
                        if (newType != null && newType.equals(task) && TaskKeys.STATE_IN_PROGRESS.equals(state)
                                && TaskRegistry.get(newType) != null) {
                            LittleMaidMoreAction.LOGGER.debug("[TaskTickHandler] TLM_SWITCH '{}' matches current flow task, skip", newType);
                            continue;
                        }
                        if (newType != null && TaskRegistry.get(newType) != null) {
                            // v64: 保存target再cancel (clearAll会清除TASK_TARGET)
                            String savedTarget = data.getString(TaskKeys.TASK_TARGET);
                            TaskDispatcher.cancel(maid);
                            TaskDispatcher.submit(maid, newType, savedTarget, 0);
                            continue;
                        }
                    }
                    TaskDispatcher.cancel(maid);
                    continue;
                }

                String guiInit = data.getString(TaskKeys.GUI_INIT);
                if (!guiInit.isEmpty()) {
                    data.remove(TaskKeys.GUI_INIT);
                    TaskDispatcher.submit(maid, guiInit, null, 0);
                    continue;
                }

                // ── 非活跃状态 或 CANCELLED ──
                if (!TaskKeys.STATE_IN_PROGRESS.equals(state)) {
                    if (TaskKeys.STATE_CANCELLED.equals(state)) cleanupMaid(maid);
                    continue;
                }
                if (task.isEmpty()) continue;

                // ── 超时看门狗 (v64: 从 TaskEngine 迁入, 仅 isLongRunning) ──
                var h = TaskRegistry.get(task);
                if (h != null && h.pipeline().isLongRunning()) {
                    long lastTick = FlowTaskData.getTick(maid);
                    if (lastTick != 0) {
                        int timeout = data.getInt(TaskKeys.FLOW_TIMEOUT);
                        if (timeout <= 0) timeout = DEFAULT_TIMEOUT;
                        if (lastTick > now) {
                            long skew = lastTick - now;
                            if (skew > 1_728_000L) {
                                LittleMaidMoreAction.LOGGER.warn("[TaskTickHandler] task '{}' stale (skew={}), cleaning via Dispatcher", task, skew);
                                TaskDispatcher.timeout(maid);
                            }
                            continue;
                        }
                        if (now - lastTick > timeout) {
                            LittleMaidMoreAction.LOGGER.info("[TaskTickHandler] task '{}' timed out ({}t > {}t)", task, now - lastTick, timeout);
                            TaskDispatcher.timeout(maid);
                        }
                    }
                }

                // ── GameTick 管线驱动 ──
                if (h != null && h.pipeline().needsGameTick()) {
                    TaskStateManager.heartbeat(maid, now);
                    h.pipeline().tick(sl, maid);
                }
            }
            tickPassive(sl);
            // v63: EnvSense 全局广播 (自节流 200tick)
            tickBroadcast(sl);
        }
    }

    /** v63: EnvSense 广播 — 按 config 间隔节流 */
    private static void tickBroadcast(ServerLevel sl) {
        long now = sl.getGameTime();
        if (now < nextBroadcastTick) return;
        int interval = MoreActionConfig.ENV_SCAN_INTERVAL.get();
        nextBroadcastTick = now + interval;
        EnvSenseBroadcaster.broadcast(sl);
    }

    private static void tickPassive(ServerLevel sl) {
        for (var e : sl.getAllEntities()) {
            if (!(e instanceof EntityMaid maid)) continue;
            TaskRegistry.passiveTasks().forEach(h -> {
                String key = TaskKeys.passiveKey(h.taskType());
                if (TaskKeys.STATE_IN_PROGRESS.equals(maid.getPersistentData().getString(key))
                        && TaskToggle.isEnabledFor(maid, h.taskType())) {
                    h.pipeline().tick(sl, maid);
                }
            });
        }
    }

    private static void cleanupMaid(EntityMaid maid) {
        String task = FlowTaskData.getTask(maid);
        if (task.isEmpty()) return;
        TaskStateManager.clearAll(maid);
    }
}
