package com.github.xiaozhaoz1.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
//? if 1.20.1 {
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

/**
 * v79.47: 图鉴击杀计数监听 — 独立订阅类 (不并入 TlmEventAdapter, InvariantTest 守 2 订阅者)。
 *
 * <p>2026-08-15 审计 H1 迁层: 原 vanilla/input/entity 是 io 读原语层, 事件订阅者写 PD
 * 违反两轴分层 — 归位 event/ 包 (事件桥层)。
 *
 * <p>判定模式照抄 TLM EntityDeathEvent (凶手 = 女仆); 计数写女仆 PD 根键
 * {@code lma_codex} (CompoundTag: 实体注册名 → 击杀数), 跨 session 持久 (图鉴收藏, 不清理)。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class MaidCodexKillListener {

    /** 图鉴计数 PD 根键 — 跨 session 持久 (实体注册名 → 击杀数; v79.55 收编 TaskKeys) */
    public static final String CODEX_KEY = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskKeys.CODEX;

    private MaidCodexKillListener() {}

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        var source = event.getSource();
        if (source == null) return;
        if (!(source.getEntity() instanceof EntityMaid maid)) return;
        // LivingDeathEvent.getEntity() 已返回 LivingEntity — 模式匹配同类型编译报错, 直取
        LivingEntity target = event.getEntity();
        if (maid.level().isClientSide()) return;

        CompoundTag pd = maid.getPersistentData();
        CompoundTag codex = pd.getCompound(CODEX_KEY);
        String id = entityIdOf(target);
        codex.putInt(id, codex.getInt(id) + 1);
        pd.put(CODEX_KEY, codex);
    }

    /** 实体注册名 (双平台条件化) */
    private static String entityIdOf(LivingEntity target) {
        //? if 1.20.1 {
        return net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                .getKey(target.getType()).toString();
        //?} else {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(target.getType()).toString();
        //?}
    }

    /** 读取女仆图鉴计数 (实体注册名 → 击杀数; 无 → 空) */
    public static CompoundTag readCodex(EntityMaid maid) {
        return maid.getPersistentData().getCompound(CODEX_KEY);
    }
}
