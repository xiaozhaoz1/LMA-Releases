package com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.bauble.BaubleApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableFloat;

/**
 * 野生酒狐奶饰品 (v79.6x) — 濒死触发无敌 (图腾式) + 音乐 + 专属 CD。
 *
 * <p>触发 (onDeath): 濒死且不在 CD → 取消死亡 + 回 1 血 + 持续无敌 (invincible_ticks) +
 * 播音乐 + 进 CD (cd_ticks); 饰品保留 (无法破坏语义, 永不 hurtAndBreak/shrink)。
 * 无敌期 (onInjured): 直接取消伤害。
 * 状态存 PersistentData (BaubleApi 时间戳键, 跨 session 持久; 键闭环由 onTakeOff/卸载清理)。
 */
public final class WildMilkBauble implements IMaidBauble {

    /** BaubleApi 键 (bauble/ 层通用前缀) */
    private static final String KEY_INVINCIBLE = "wild_milk_invincible";
    private static final String KEY_CD = "wild_milk_cd";

    @Override
    public boolean onDeath(EntityMaid maid, ItemStack baubleItem, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false; // 无法抵抗的伤害不触发 (图腾同款)
        }
        long now = maid.level().getGameTime();
        if (BaubleApi.onCooldown(maid, KEY_CD, now)) {
            return false; // CD 中
        }
        int invincible = WildKitsuneMilkConfig.WILD_INVINCIBLE_TICKS.get();
        int cd = WildKitsuneMilkConfig.WILD_CD_TICKS.get();
        // 取消死亡 → 回 1 血 + 无敌 + CD
        maid.setHealth(1.0F);
        BaubleApi.writeUntil(maid, KEY_INVINCIBLE, now + invincible);
        BaubleApi.writeUntil(maid, KEY_CD, now + cd);
        // 音乐 (服务端女仆位置播放, 附近全听; 时长 = 无敌时长; 音量默认 0.5 = 50% 可调 bug 6)
        if (maid.level() instanceof ServerLevel level) {
            float volume = WildKitsuneMilkConfig.WILD_MUSIC_VOLUME.get().floatValue();
            level.playSound(null, maid.blockPosition(),
                    com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaSounds.DOGMILK.get(),
                    SoundSource.NEUTRAL, volume, 1.0F);
            // 图腾式爆粒子 (bug 7 — 同不死图腾 TOTEM_OF_UNDYING 跟踪粒子)
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    maid.getX(), maid.getY() + 1.0, maid.getZ(), 60, 0.35, 0.5, 0.35, 0.2);
        }
        return true;
    }

    @Override
    public boolean onInjured(EntityMaid maid, ItemStack baubleItem, DamageSource source, MutableFloat damage) {
        long now = maid.level().getGameTime();
        // 无敌期内 → 取消伤害
        if (BaubleApi.onCooldown(maid, KEY_INVINCIBLE, now)) {
            damage.setValue(0F);
            return true;
        }
        return false;
    }

    /** 无敌期内每 3s (60 tick) 播一次不死图腾特效 (用户裁定) */
    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        long now = maid.level().getGameTime();
        if (!BaubleApi.onCooldown(maid, KEY_INVINCIBLE, now)) return; // 不在无敌期
        if (now % 60 != 0) return; // 每 3s 一次
        if (maid.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                    maid.getX(), maid.getY() + 1.0, maid.getZ(), 30, 0.35, 0.5, 0.35, 0.2);
        }
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        // 键闭环 — 卸下时清残留 (不再无敌)
        BaubleApi.clearUntil(maid, KEY_INVINCIBLE);
    }
}
