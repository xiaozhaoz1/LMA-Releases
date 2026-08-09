package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.ChainHarvestExecute;
//? if 1.20.1 {
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
//?}

/**
 * v79.27: 实体卸载清理 — 女仆离开世界时清除按 maid.getId() key 的静态缓存
 * (ChainHarvestExecute 挖矿缓存 / PathingApi 导航看门狗 / GameTickPipelineManager 被动位掩码)。
 *
 * <p>防长期服务器内存增长 + MC 实体 ID 复用串扰 (新女仆继承旧状态: 跳过集残留不挖矿 /
 * 气泡节流错乱 / LAST_MODE 跨任务残留)。
 *
 * <p>独立订阅类 — TlmEventAdapter 守 2 订阅者 (InvariantTest 反射守护), 不可并入。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class EntityCleanupListener {

    private EntityCleanupListener() {}

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            ChainHarvestExecute.clearMaidState(maid);
            PathingApi.clearNav(maid);
            GameTickPipelineManager.clearMaidCaches(maid);
        }
    }
}
