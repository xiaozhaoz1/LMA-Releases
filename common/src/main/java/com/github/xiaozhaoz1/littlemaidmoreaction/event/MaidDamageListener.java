package com.github.xiaozhaoz1.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDamageEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime.TaskDispatcher;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.SelfRescueState;
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

/**
 * v79.58: 女仆受伤监听 — TLM MaidDamageEvent (双平台现成: 1.20 MaidLivingEntityEvent
 * 转发 / 1.21 actuallyHurt post; 触发 = 最终扣血前, 带 DamageSource)。
 *
 * <p>掉血 → 记录自救上下文 (SelfRescueState) + 启动自救被动任务 (self_rescue)。
 * 瞬破动作全在被动 tick 内 (用户裁定: 纯被动响应 — 预留未来更多自救方法);
 * 哈气互斥由 submitPassive 内部挡 (哈气优先裁定)。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class MaidDamageListener {

    private MaidDamageListener() {}

    @SubscribeEvent
    public static void onMaidDamage(MaidDamageEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) return;
        // 掉血快照 (事件在扣血前 — 推算掉血后血量比例)
        SelfRescueState.record(maid, event.getAmount());
        // 启动自救被动 — 已在运行则覆盖写同值 (幂等); 哈气运行中被互斥挡 (哈气优先)
        TaskDispatcher.submitPassive(maid, "self_rescue");
    }
}
