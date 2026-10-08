package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidData;

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

    /** 上轮 TLM 任务 uid (键 = 女仆 UUID, v79.63 统一 — 见 {@code task.data.MaidKey})。
     *  原为实体 ID 键: ID 复用 + 漏清理会让新女仆继承旧女仆的 uid → 首次 tick 误判"切换"并写 TLM_SWITCH 标记。 */
    private static final Map<java.util.UUID, ResourceLocation> LAST_TASK = new HashMap<>();

    private TlmTaskMonitor() {}

    @SubscribeEvent
    public static void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) return;

        var maidTask = maid.getTask();
        if (maidTask == null) return;
        ResourceLocation currentTask = maidTask.getUid();
        ResourceLocation lastTask = LAST_TASK.put(
                com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidKey.uuid(maid), currentTask);

        if (lastTask != null && !lastTask.equals(currentTask)) {
            // 写 NBT 标记 → 轮询消费 (门面收编)
            com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskMetaData.setTlmSwitch(maid, currentTask.toString());
            LittleMaidMoreAction.LOGGER.debug("[LMA/TaskMonitor] switch detected {} → {}",
                lastTask, currentTask);
        }
    }

    /** HashMap key 闭环 — 女仆离开世界时清理 (v79.63 键改 UUID, 形参随之) */
    public static void onMaidLeave(EntityMaid maid) {
        LAST_TASK.remove(com.github.xiaozhaoz1.littlemaidmoreaction.task.data.MaidKey.uuid(maid));
    }
}
