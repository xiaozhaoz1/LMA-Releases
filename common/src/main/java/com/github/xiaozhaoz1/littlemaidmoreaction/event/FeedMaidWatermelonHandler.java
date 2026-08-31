package com.github.xiaozhaoz1.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

/**
 * 喂女仆西瓜 (v79.62.1) — 玩家主手西瓜右键已驯服女仆 → 消耗 1 西瓜, 女仆回血 + 好感 +1 + 爱心 + 气泡.
 *
 * <p><b>通用识别</b>: 注册名路径含 melon 且可食用 — 覆盖原版 melon_slice + 各模组 watermelon/melon 变体
 * (与炸鸡配西瓜组合同款识别函数).
 *
 * <p><b>触发</b>: TLM {@link InteractMaidEvent} 只在「已驯服 + 主人」右键女仆时 fire (EntityMaid.mobInteract
 * L662 isOwnedBy) — 玩家只能喂自己的女仆. 取消事件阻止打开女仆 GUI.
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class FeedMaidWatermelonHandler {

    /** 加好感 CD (tick) — 与挤奶一致 5 分钟 */
    private static final int FAVOR_CD_TICKS = 6000;
    /** 好感时间戳键 (maid PD) */
    private static final String KEY_FAVOR_CD = "lma_watermelon_favor_cd";
    /** 喂食回血 (半颗心) — 西瓜片原版恢复 2 饥饿, 女仆按此回血 */
    private static final float HEAL_AMOUNT = 4.0F;

    /** 气泡文案池 (随机) */
    private static final String[] BUBBLES = {
            "西瓜好甜，谢谢狗修金~",
            "狗修金喂我吃西瓜，好开心！",
            "唔…好凉快！再来一块西瓜吧？",
            "西瓜解渴又解暑，狗修金真贴心！",
    };

    /** 炸鸡配西瓜组合气泡 (喂西瓜 + 女仆背包有炸鸡 → 组合效果) */
    private static final String[] COMBO_BUBBLES = {
            "炸鸡配西瓜好好吃！谢谢狗修金！",
            "西瓜和炸鸡一起，狗修金最懂我~",
            "唔…炸鸡的油配西瓜的清甜，绝配！",
            "狗修金喂我炸鸡配西瓜，太幸福啦！",
    };

    /** 组合额外好感 CD (tick) — 60 秒 (只锁好感, 组合气泡/加速/爱心无 CD) */
    private static final int COMBO_FAVOR_CD_TICKS = 1200;
    /** 组合额外好感 CD 时间戳键 (maid PD) */
    private static final String KEY_COMBO_FAVOR_CD = "lma_watermelon_combo_favor_cd";
    /** 组合加速 I 时长 (tick) — 30 秒 */
    private static final int COMBO_SPEED_TICKS = 600;

    private FeedMaidWatermelonHandler() {}

    @SubscribeEvent
    public static void onInteractMaid(InteractMaidEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide()) return;
        ItemStack stack = event.getStack();
        if (!isWatermelon(stack)) return;
        EntityMaid maid = event.getMaid();
        if (maid == null) return;

        // 消耗 1 西瓜
        stack.shrink(1);
        // 回血
        if (maid.getHealth() < maid.getMaxHealth()) {
            maid.heal(HEAL_AMOUNT);
        }
        // 好感 +1 (CD 5min)
        addFavorIfReady(maid);
        // 爱心粒子 + 气泡
        spawnHeartParticles(maid);
        // v79.62.1 组合: 喂西瓜 + 女仆背包有炸鸡 → 触发「炸鸡配西瓜」组合效果
        // 组合气泡/加速/爱心无 CD (每次有炸鸡就触发); 仅组合额外好感有 60s CD
        if (hasFriedChicken(maid)) {
            maid.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, COMBO_SPEED_TICKS, 0));
            spawnHeartParticles(maid);
            com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi.showInfo(
                    maid, COMBO_BUBBLES[maid.getRandom().nextInt(COMBO_BUBBLES.length)]);
            addComboFavorIfReady(maid);  // 组合额外 +1 好感 (60s CD)
        } else {
            com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi.showInfo(
                    maid, BUBBLES[maid.getRandom().nextInt(BUBBLES.length)]);
        }
        // 取消事件阻止打开女仆 GUI
        event.setCanceled(true);
    }

    /** 组合额外好感 (60s CD) — 仅锁好感, 组合其他效果无 CD */
    private static void addComboFavorIfReady(EntityMaid maid) {
        long now = maid.level().getGameTime();
        long cdUntil = maid.getPersistentData().getLong(KEY_COMBO_FAVOR_CD);
        if (cdUntil > now) return;
        maid.getFavorabilityManager().add(1);
        maid.getPersistentData().putLong(KEY_COMBO_FAVOR_CD, now + COMBO_FAVOR_CD_TICKS);
    }

    /** 女仆背包是否有炸鸡 (可食用过滤) — 与 FriedChickenComboHandler 同款识别 */
    private static boolean hasFriedChicken(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || !isEdible(s)) continue;
            String path = itemPath(s).replace("_", "").replace("item", "");
            if (path.contains("friedchicken") || (path.contains("fried") && path.contains("chicken"))) {
                return true;
            }
        }
        return false;
    }

    /** 加 1 好感 (CD 5min) */
    private static void addFavorIfReady(EntityMaid maid) {
        long now = maid.level().getGameTime();
        long cdUntil = maid.getPersistentData().getLong(KEY_FAVOR_CD);
        if (cdUntil > now) return;
        maid.getFavorabilityManager().add(1);
        maid.getPersistentData().putLong(KEY_FAVOR_CD, now + FAVOR_CD_TICKS);
    }

    /** 西瓜识别 — 注册名路径含 melon 且可食用 (种子非食物不误判) */
    static boolean isWatermelon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!isEdible(stack)) return false;
        String path = itemPath(stack).toLowerCase();
        return path.contains("melon");
    }

    /** 可食用判定 (双平台) */
    private static boolean isEdible(ItemStack stack) {
//? if 1.20.1 {
        return stack.isEdible();
//?} else {
        return stack.has(net.minecraft.core.component.DataComponents.FOOD);
//?}
    }

    /** 物品注册名 path (双平台注册表) */
    private static String itemPath(ItemStack stack) {
//? if 1.20.1 {
        net.minecraft.resources.ResourceLocation rl = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return rl == null ? "" : rl.getPath();
//?} else {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
//?}
    }

    /** 爱心粒子 (参考 KitsuneMilkInteract) */
    private static void spawnHeartParticles(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel sl)) return;
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                maid.getX(), maid.getEyeY() + 0.4, maid.getZ(),
                5, 0.35, 0.2, 0.35, 0.05);
    }
}
