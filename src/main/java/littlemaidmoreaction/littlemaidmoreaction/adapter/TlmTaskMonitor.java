package littlemaidmoreaction.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.task.TaskKeys;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * TLM 任务切换监听 (v49).
 *
 * <p>v49: 不再直接调用 TaskDispatcher。写入 NBT 标记，由 TaskEngine 轮询决策。
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class TlmTaskMonitor {

    private static final Map<Integer, ResourceLocation> LAST_TASK = new WeakHashMap<>();

    private TlmTaskMonitor() {}

    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) return;

        var maidTask = maid.getTask();
        if (maidTask == null) return;
        ResourceLocation currentTask = maidTask.getUid();
        ResourceLocation lastTask = LAST_TASK.put(maid.getId(), currentTask);

        if (lastTask != null && !lastTask.equals(currentTask)) {
            // v49: 写 NBT 标记 → TaskEngine 轮询 → TaskDispatcher.cancel()
            maid.getPersistentData().putString(TaskKeys.TLM_SWITCH, currentTask.toString());
            LittleMaidMoreAction.LOGGER.debug("[LMA/TaskMonitor] switch detected {} → {}",
                lastTask, currentTask);
        }
    }
}
