package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if 1.20.1 {
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

/**
 * 挤奶交互 (v79.6x) — 玩家主手空桶右键女仆。
 *
 * <p><b>三态判定 (主人维度)</b> — TLM mobInteract 事实:
 * {@link InteractMaidEvent} 只在「已驯服 + 主人」右键时 fire (EntityMaid L662 isOwnedBy);
 * 未驯服右键走 tameMaid (不 fire InteractMaidEvent)。故拆两条监听:
 * <ul>
 *   <li>已驯服 + 主人 → {@link InteractMaidEvent}: 酒狐奶桶 + 加 1 好感 (CD 5min, 仅好感有 CD)</li>
 *   <li>未驯服 (ownerUUID == null) → {@code PlayerInteractEvent.EntityInteract}: 野生奶(副开关开)/奶桶(副开关关) + 哈气动画 + 攻击</li>
 *   <li>已驯服 + 别人 → 两事件都不产奶 (InteractMaidEvent 不 fire; EntityInteract 里 decide 判 null)</li>
 * </ul>
 * 仅挤奶无 CD; 攻击伤害读哈气管线 {@code PassiveTaskConfig.HAQI_HIT_DAMAGE} (用户裁定)。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class KitsuneMilkInteract {

    /** 加好感 CD (tick) — 硬编码 5 分钟 (用户裁定不进配置) */
    private static final int FAVOR_CD_TICKS = 6000;
    /** 好感时间戳键 */
    private static final String KEY_FAVOR_CD = "wild_milk_favor_cd";

    private KitsuneMilkInteract() {}

    /**
     * 已驯服 + 主人 → 挤奶 + 加好感 (InteractMaidEvent 只在此路径 fire)。
     */
    @SubscribeEvent
    public static void onInteractTamed(InteractMaidEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide()) return;
        if (!isBucket(event.getStack())) return;
        EntityMaid maid = event.getMaid();
        if (!WildKitsuneMilkConfig.TOGGLE_ENABLED.get()) return;
        // 此时必已驯服+主人 (事件只在此 fire) — 给好感 + 产奶 + 心形粒子
        addFavorIfReady(maid, player);
        produce(player, MilkKind.TAMED);
        spawnHeartParticles(maid);
        event.setCanceled(true); // 阻止 TLM 后续右键
    }

    /**
     * 未驯服女仆 → 挤奶 + 哈气动画 + 攻击 (EntityInteract 在 mobInteract 前 fire, 可取消)。
     * ⚠ EntityInteract 对所有实体交互都 fire (且可能同一次右键 fire 多次) — 故这里必须:
     * (1) 只用 isTame() 判未驯服 (对齐 TLM tameMaid L681 的 !isTame, 非 ownerUUID);
     * (2) 去重守卫防一次右键产两个奶。
     */
    @SubscribeEvent
    public static void onInteractWild(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!isBucket(player.getMainHandItem())) return;
        Entity target = event.getTarget();
        if (!(target instanceof EntityMaid maid)) return;
        if (maid.isTame()) return;                    // 已驯服 (自己/别人) 都不走这条
        if (!WildKitsuneMilkConfig.TOGGLE_ENABLED.get()) return;
        if (!dedupe(maid, player)) return;            // 同一次右键重复 fire → 只产一次

        // 未驯服: 副开关决定野生奶还是奶桶
        MilkKind kind = WildKitsuneMilkConfig.TOGGLE_WILD_EXTRA.get() ? MilkKind.WILD : MilkKind.TAMED;

        // 和驯服一样: 冒爱心 + 产奶; 不打人不哈气, 也不加好感 (用户裁定)
        produce(player, kind);
        spawnHeartParticles(maid);
        event.setCancellationResult(InteractionResult.CONSUME);
        event.setCanceled(true);
    }

    /** 去重守卫 — 同一 tick 内对同一女仆的挤奶只执行一次 (防 EntityInteract 重复 fire 产双奶) */
    private static boolean dedupe(EntityMaid maid, Player player) {
        long now = maid.level().getGameTime();
        long stored = player.getPersistentData().getLong("lma_kitsune_milk_dedupe_tick");
        if (stored == now) return false;
        player.getPersistentData().putLong("lma_kitsune_milk_dedupe_tick", now);
        return true;
    }

    private static boolean isBucket(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.BUCKET);
    }

    /** 加 1 好感 (CD 5min, 仅好感有 CD; 挤奶本身无 CD) */
    private static void addFavorIfReady(EntityMaid maid, Player player) {
        long now = maid.level().getGameTime();
        long cdUntil = maid.getPersistentData().getLong(KEY_FAVOR_CD);
        if (cdUntil > now) return;
        maid.getFavorabilityManager().add(1);
        maid.getPersistentData().putLong(KEY_FAVOR_CD, now + FAVOR_CD_TICKS);
    }

    /** 扣 1 空桶 + 给对应奶桶 (主手优先); 挤奶音效 = 原版 COW_MILK (TLM MaidMilkTask 同款) */
    private static void produce(Player player, MilkKind kind) {
        ItemStack stack = player.getMainHandItem();
        stack.shrink(1);
        ItemStack milk = new ItemStack(kind == MilkKind.TAMED
                ? KitsuneMilkItems.TAMED_MILK_BUCKET.get()
                : KitsuneMilkItems.WILD_DOGMILK.get());
        if (!player.getInventory().add(milk)) {
            player.drop(milk, false);
        }
        // 挤奶音效 (原版 COW_MILK)
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.COW_MILK,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        // 获得经验音效 (bug 5 — 原版经验球拾取声)
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    /** 挤奶心形粒子 (v79.6x) — 女仆头顶撒 5 颗 HEART (原版粒子, 服务端 sendParticles 附近可见) */
    private static void spawnHeartParticles(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel sl)) return;
        double x = maid.getX();
        double y = maid.getEyeY() + 0.4;
        double z = maid.getZ();
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                x, y, z, 5, 0.35, 0.2, 0.35, 0.05);
    }

    /**
     * 三态决策纯函数 (JVM 可测):
     * @param ownerUuid   女仆 owner (null = 未驯服)
     * @param playerUuid  挤奶玩家
     * @param mainEnabled 主开关
     * @param wildExtra   副开关 (未驯服时: true=野生奶, false=奶桶)
     * @return TAMED/WILD=产对应奶, null=不能挤 (已驯服但别人的 / 主开关关)
     */
    static MilkKind decide(java.util.UUID ownerUuid, java.util.UUID playerUuid,
                           boolean mainEnabled, boolean wildExtra) {
        if (!mainEnabled || playerUuid == null) return null;
        if (ownerUuid != null) {
            // 已驯服: 只允许主人 (UUID 比较, 错题 #130 禁 isOwnedBy 引用比较)
            return ownerUuid.equals(playerUuid) ? MilkKind.TAMED : null;
        }
        // 未驯服: 副开关决定野生奶还是奶桶
        return wildExtra ? MilkKind.WILD : MilkKind.TAMED;
    }
}

