package littlemaidmoreaction.littlemaidmoreaction.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * v53: 通用 game-tick 驱动.
 * v61: 新增被动任务 tick (与主动任务并行).
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class TaskTickHandler {

    private TaskTickHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel sl : event.getServer().getAllLevels()) {
            for (var e : sl.getAllEntities()) {
                if (!(e instanceof EntityMaid maid)) continue;
                String state = LmaTaskDataHelper.getFlowState(maid);
                if (!TaskKeys.STATE_IN_PROGRESS.equals(state)) {
                    if (TaskKeys.STATE_CANCELLED.equals(state)) cleanupMaid(maid);
                    continue;
                }
                String task = LmaTaskDataHelper.getFlowTask(maid);
                if (task.isEmpty()) continue;
                var h = TaskRegistry.get(task);
                if (h != null && h.pipeline().needsGameTick()) {
                    TaskStateManager.heartbeat(maid, sl.getGameTime());
                    h.pipeline().tick(sl, maid);
                }
            }
            tickPassive(sl);
        }
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
        String task = LmaTaskDataHelper.getFlowTask(maid);
        if (task.isEmpty()) return;
        var h = TaskRegistry.get(task);
        if (h != null) h.pipeline().onCleanup(maid);
        TaskStateManager.clearAll(maid);
    }
}
