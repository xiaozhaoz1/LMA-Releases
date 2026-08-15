package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 酒狐奶桶 (v79.6x) — 可饮 (抗性提升 II + 生命恢复 I) + 可装备为饰品。
 *
 * <p>饮用: 服务端给效果, 喝后返回空桶 ({@link Items#BUCKET})。
 * 饰品行为见 {@link TamedMilkBauble} (受伤害触发双 buff + 掉耐久)。
 */
public final class TamedMilkBucketItem extends Item {

    public TamedMilkBucketItem() {
        super(new Item.Properties().stacksTo(1).durability(30));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
//? if 1.20.1 {
    public int getUseDuration(ItemStack stack) {
//?} else {
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
//?}
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            int resistance = WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS.get();
            int regen = WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS.get();
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, resistance, 1)); // II
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 0));          // I
            // 喝奶音效 (原版 GENERIC_DRINK, TLM ApplyPotionEffectEvent 同款)
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.GENERIC_DRINK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6F,
                    0.8F + player.getRandom().nextFloat() * 0.4F);
        }
        // 喝后返回空桶 (生存模式; 创造模式不消耗原桶)
        if (entity instanceof Player p && p.getAbilities().instabuild) {
            return stack;
        }
        ItemStack result = new ItemStack(Items.BUCKET);
        if (entity instanceof Player p) {
            return ItemUtils.createFilledResult(stack, p, result, false);
        }
        return result;
    }
}
