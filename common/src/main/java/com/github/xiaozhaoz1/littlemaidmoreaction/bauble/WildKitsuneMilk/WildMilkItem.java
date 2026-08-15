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
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * 野生酒狐奶 (v79.6x) — 可饮 (生命恢复 I 30s) + 可装备为饰品。
 *
 * <p>饮用: 服务端给效果, 喝后返回空桶。
 * 饰品行为见 {@link WildMilkBauble} (濒死触发无敌 + 音乐 + 10 分钟 CD, 无法破坏)。
 */
public final class WildMilkItem extends Item {

    public WildMilkItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    /** 工具提示: 附上「无法破坏」标注 (bug 8 — 野生奶触发无敌不消耗) */
    @Override
//? if 1.20.1 {
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level,
                                java.util.List<net.minecraft.network.chat.Component> tooltipComponents,
                                net.minecraft.world.item.TooltipFlag tooltipFlag) {
//?} else {
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                java.util.List<net.minecraft.network.chat.Component> tooltipComponents,
                                net.minecraft.world.item.TooltipFlag tooltipFlag) {
//?}
        tooltipComponents.add(net.minecraft.network.chat.Component.translatable("item.unbreakable")
                .withStyle(net.minecraft.ChatFormatting.BLUE));
//? if 1.20.1 {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
//?} else {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
//?}
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
            int regen = WildKitsuneMilkConfig.WILD_REGENERATION_TICKS.get();
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 0)); // I
            // 喝奶音效 (原版 GENERIC_DRINK)
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.GENERIC_DRINK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6F,
                    0.8F + player.getRandom().nextFloat() * 0.4F);
        }
        if (entity instanceof Player p && p.getAbilities().instabuild) {
            return stack;
        }
        ItemStack result = new ItemStack(Items.BUCKET);
        if (entity instanceof Player p) {
            return ItemUtils.createFilledResult(stack, p, result, false);
        }
        return result;
    }

    // 「无法破坏」语义由饰品行为保证 (WildMilkBauble 永不 hurtAndBreak/shrink);
    // 饮用/装备均不消耗本体 (用户裁定: 触发无敌后饰品保留, 仅进 CD)。
}
