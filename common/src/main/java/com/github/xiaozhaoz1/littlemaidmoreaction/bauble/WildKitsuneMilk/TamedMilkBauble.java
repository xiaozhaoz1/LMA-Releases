package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableFloat;
import net.minecraft.world.damagesource.DamageSource;

/**
 * 酒狐奶桶饰品 (v79.6x) — 女仆受伤时触发: 抗性提升 II + 生命恢复 I + 掉 1 耐久 (总耐久 30)。
 * 不取消伤害 (只加 buff)。
 */
public final class TamedMilkBauble implements IMaidBauble {

    @Override
    public boolean onInjured(EntityMaid maid, ItemStack baubleItem, DamageSource source, MutableFloat damage) {
        // 不取消伤害; 每次受伤给双 buff + 掉耐久
        int resistance = WildKitsuneMilkConfig.TAMED_RESISTANCE_TICKS.get();
        int regen = WildKitsuneMilkConfig.TAMED_REGENERATION_TICKS.get();
        maid.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, resistance, 1)); // II
        maid.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 0));           // I
//? if 1.20.1 {
        baubleItem.hurtAndBreak(1, maid, m -> m.sendItemBreakMessage(baubleItem));
//?} else {
        maid.hurtAndBreak(baubleItem, 1);
//?}
        return false;
    }
}
