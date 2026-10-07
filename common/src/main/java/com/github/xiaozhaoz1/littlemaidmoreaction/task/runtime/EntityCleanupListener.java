package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing.PathingApi;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute;
//? if 1.20.1 {
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
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
        // 统一卸载清理注册表 (含 PL 内存态 flush + 13 静态缓存 + 2 泄漏修复)
        if (event.getEntity() instanceof EntityMaid maid) {
            // 正式诊断 (2026-09-21, 用户批准"探针铺满"): 每次女仆移除记一行 — 谁清的(框架帧) + 原因
            //   与 [DIAG-SPAWN]/[DIAG-HB]/[DIAG-SUCCEED] 按 uuid 对账 ⇒ 一次跑即可定案
            {
                String fw = "-";
                for (StackTraceElement e : new Throwable().getStackTrace()) {
                    if (e.getClassName().contains("minecraft.gametest.framework")) {
                        fw = e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1) + "." + e.getMethodName();
                        break;
                    }
                }
                LittleMaidMoreAction.LOGGER.warn("[DIAG-LEAVE] uuid={} pos={} reason={} gametime={} 框架帧={}",
                        maid.getUUID(), maid.blockPosition().toShortString(), maid.getRemovalReason(),
                        maid.level().getGameTime(), fw);
            }
            MaidIndex.remove(maid);   // v79.66: 在册女仆索引移除 (TaskTickHandler 只遍历在册 ✓)
            MaidUnloadRegistry.runAll(maid);
        }
    }

    /** v79.66: 女仆加入世界 ⇒ 登记进索引 (心跳遍历只走索引, 不再全实体扫描) */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !event.getLevel().isClientSide) {
            MaidIndex.add(maid);
        }
    }
}
