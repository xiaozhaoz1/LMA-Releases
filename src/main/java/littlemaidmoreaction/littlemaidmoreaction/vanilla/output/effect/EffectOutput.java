package littlemaidmoreaction.littlemaidmoreaction.vanilla.output.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** 药水效果输出 — 覆盖 ~2 个 impl/action/effect/ 类 */
public final class EffectOutput {
    private EffectOutput() {}

    public static void apply(LivingEntity target, String effectId, int duration, int amplifier, boolean ambient) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(effectId));
        if (effect == null) return;
        target.addEffect(new MobEffectInstance(effect, duration, amplifier, ambient, true));
    }
    public static void clearAll(LivingEntity target) { target.removeAllEffects(); }
    /** 移除指定效果 */
    public static void clearEffect(LivingEntity target, String effectId) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(effectId));
        if (effect != null) target.removeEffect(effect);
    }
}
