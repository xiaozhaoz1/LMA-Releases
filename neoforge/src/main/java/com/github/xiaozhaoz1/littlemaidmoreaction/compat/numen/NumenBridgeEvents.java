package com.github.xiaozhaoz1.littlemaidmoreaction.compat.numen;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitItems;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.NumenCompat;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.AiControlGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Numen 假人桥事件监听 (v75) — 石板放出交接 + 手动收起路径。
 *
 * <p>① EntityJoinLevelEvent: 石板放出的新女仆实体 join → onMaidRestored 交接
 * (假人销毁 + 物品全量归还新女仆 + 绑定清理)。
 * ② InteractMaidEvent (TLM, HIGHEST): 玩家拿<b>空石板</b>右键运行中女仆 (ai_control gate on)
 * → 先销毁假人 + 物品搬回女仆 + cancel 任务 (idle) → 放行 TLM SlabClickEvent 正常收起
 * (石板 = 完整 idle 女仆, 防放女仆恢复 gate 循环 — 用户硬性要求)。
 *
 * <p>v74 的伤害隔离/挡射线/离场清理随镜像架构删除 (假人独立无女仆镜像对象)。
 */
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
public final class NumenBridgeEvents {

    /** 石板放出 → 新女仆 join → 交接 (假人销毁 + 物品归还) */
    @SubscribeEvent
    public static void onMaidJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        // v75.4: Numen 未装时零开销 — 桥 <clinit> 引用 numen-api 类 (CompanionLifecycle),
        // 无门控 = 专用服务器每次女仆 join 即 NoClassDefFoundError (neoforge gametest 实证)
        if (!NumenCompat.isInstalled()) return;
        if (event.getEntity() instanceof EntityMaid maid
                && event.getLevel() instanceof ServerLevel sl) {
            NumenMaidBridge.onMaidRestored(maid, sl);
        }
    }

    /**
     * v79.2: 重启/重连恢复 — 玩家登录 (LOWEST, Numen respawnAllOwnedBy NORMAL 之后):
     * 追踪魂符 (背包石板 lma_companion 键) → 在线假人对齐玩家旁。
     * 覆盖: ① roster 残留 .dat 旧位置复活 ② 服务器未重启时假人自主游走残留。
     * 仅登录一次, 不影响登录后自主游走。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!NumenCompat.isInstalled()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            NumenMaidBridge.alignRestoredCompanions(player);
        }
    }

    /** 手动收起路径: 空石板右键运行中女仆 → 桥先收尾, TLM 再正常存石板 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractMaid(InteractMaidEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid == null || maid.level().isClientSide) return;
        // v75.4: Numen 门控 (同 onMaidJoin — 桥类仅 Numen 安装时可初始化)
        if (!NumenCompat.isInstalled()) return;
        if (!(maid.level() instanceof ServerLevel sl)) return;
        if (event.getStack().getItem() != InitItems.SMART_SLAB_EMPTY.get()) return;
        if (!AiControlGate.isEnabled(maid)) return;   // 非 AI 操控中 → TLM 原生行为
        // ai_control 运行中手动收起:
        // 1. 假人销毁 + 物品全量搬回 (女仆实体在)
        NumenMaidBridge.stop(maid, sl);
        // 2. 任务取消 → idle (石板存 idle, 防放女仆循环)
        TaskDispatcher.cancel(maid);
        LittleMaidMoreAction.LOGGER.info("[NumenBridge] 手动收起 ai_control 女仆 maid={} — 物品归还 + idle",
                maid.getUUID());
    }

    private NumenBridgeEvents() {}
}
