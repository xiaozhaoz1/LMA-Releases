package com.github.xiaozhaoz1.littlemaidmoreaction.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
//? if 1.20.1 {
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
//?}

/**
 * 炸鸡配西瓜组合气泡 (v79.62.1) — 女仆在任务下吃炸鸡 (背包有西瓜) 或吃西瓜 (背包有炸鸡)
 * → 触发特殊气泡 + 加速 I + 好感 +1 + 冒爱心。
 *
 * <p><b>通用识别 (注册名关键词归一化)</b>: 跨模组识别炸鸡/西瓜 — 任意模组的注册名路径
 * 去下划线/去 item 后缀后含 fried+chicken 或 melon 均命中 (Pam's friedchickenitem /
 * RusticDelight fried_chicken / Create Gourmet fried_chicken / Crafting++ soul_fried_chicken;
 * 西瓜含原版 melon_slice + 各模组 watermelon/melon 变体)。
 *
 * <p><b>触发条件</b>: 女仆执行任务时 (非 TaskIdle) 吃配对食物 + 不在 60s CD。
 * CD/好感数值用户裁定 (CD 60s + 好感 +1 + 加速 30s)。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID)
//?}
public final class FriedChickenComboHandler {

    /** 触发 CD (tick) — 60 秒 (用户裁定) */
    private static final int CD_TICKS = 1200;
    /** 加速 I 时长 (tick) — 30 秒 (用户裁定) */
    private static final int SPEED_TICKS = 600;
    /** CD 时间戳键 (maid PersistentData) */
    private static final String KEY_CD = "lma_fried_combo_cd";

    /** 特殊气泡文案池 (随机; 用户提供 + 同类语气补充) */
    private static final String[] BUBBLES = {
            "炸鸡配西瓜好好吃！",
            "不偷吃了，狗修金我现在就去干活！",
            "西瓜好甜，炸鸡也好甜~",
            "狗修金也要一块吗？不对，我不是在偷懒！",
            "嘿嘿，炸鸡的油配西瓜的清甜，绝配！",
            "嘘…狗修金没看到我在偷吃西瓜配炸鸡吧？",
    };

    private FriedChickenComboHandler() {}

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        // 只有女仆吃东西触发
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide()) return;

        ItemStack eaten = event.getItem();
        if (eaten.isEmpty()) return;

        // 判定吃的类型 + 背包配对
        boolean ateFried = isFriedChicken(eaten);
        boolean ateMelon = isWatermelon(eaten);
        if (ateFried == ateMelon) return;   // 都不是 或 都是(不可能) — 跳过
        boolean hasPair = ateFried ? hasWatermelon(maid) : hasFriedChicken(maid);
        if (!hasPair) return;

        // 女仆必须在执行任务 (非空闲) — 用户裁定
        if (maid.getTask() == null || maid.getTask() instanceof TaskIdle) return;

        // CD 检查
        long now = maid.level().getGameTime();
        if (maid.getPersistentData().getLong(KEY_CD) > now) return;
        maid.getPersistentData().putLong(KEY_CD, now + CD_TICKS);

        // 触发: 气泡 + 加速 + 好感 + 爱心
        com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble.MaidChatBubbleApi.showInfo(
                maid, BUBBLES[maid.getRandom().nextInt(BUBBLES.length)]);
        maid.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_TICKS, 0));
        maid.getFavorabilityManager().add(1);
        spawnHeartParticles(maid);
    }

    // ── 通用识别 (注册名关键词归一化) ──

    /** 炸鸡 — 注册名路径去下划线/去 item 后缀后含 fried+chicken (跨模组通用) */
    static boolean isFriedChicken(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String path = itemPath(stack).replace("_", "").replace("item", "");
        return path.contains("friedchicken") || (path.contains("fried") && path.contains("chicken"));
    }

    /** 西瓜 — 注册名路径含 melon (原版 melon_slice + 各模组 watermelon/melon 变体; 种子非食物不误判) */
    static boolean isWatermelon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return itemPath(stack).contains("melon");
    }

    /** 背包是否有炸鸡 (可食用过滤) */
    private static boolean hasFriedChicken(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (isEdible(s) && isFriedChicken(s)) return true;
        }
        return false;
    }

    /** 背包是否有西瓜 (可食用过滤) */
    private static boolean hasWatermelon(EntityMaid maid) {
        var inv = maid.getAvailableInv(true);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (isEdible(s) && isWatermelon(s)) return true;
        }
        return false;
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

    // ── 爱心粒子 (参考 KitsuneMilkInteract) ──

    private static void spawnHeartParticles(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel sl)) return;
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                maid.getX(), maid.getEyeY() + 0.4, maid.getZ(),
                5, 0.35, 0.2, 0.35, 0.05);
    }
}
