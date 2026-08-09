package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys;
import net.minecraft.resources.ResourceLocation;
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

import java.util.Map;
import java.util.HashMap;

/**
 * TLM 任务切换监听 (v49).
 *
 * <p>v49: 不再直接调用 TaskDispatcher。写入 NBT 标记，由 TaskTickHandler 轮询决策。
 * <p>v64: WeakHashMap→HashMap (防GC丢检测); +onMaidLeave 清理 (key 闭环).
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class TlmTaskMonitor {

    private static final Map<Integer, ResourceLocation> LAST_TASK = new HashMap<>();

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
            // v49: 写 NBT 标记 → TaskTickHandler 轮询 → TaskDispatcher.cancel()
            maid.getPersistentData().putString(TaskKeys.TLM_SWITCH, currentTask.toString());
            LittleMaidMoreAction.LOGGER.debug("[LMA/TaskMonitor] switch detected {} → {}",
                lastTask, currentTask);
        }
    }

    /** v64: HashMap key 闭环 — 女仆离开世界时清理 */
    public static void onMaidLeave(int entityId) {
        LAST_TASK.remove(entityId);
    }
}
