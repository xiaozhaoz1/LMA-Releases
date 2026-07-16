package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** 战斗输出 — 伤害/治疗/击退/状态/复合/弹射物/图鉴 */
public final class CombatOutput {
    private CombatOutput() {}

    // === 伤害 ===
    public static void damage(LivingEntity target, EntityMaid source, float amount) {
        target.hurt(source.damageSources().mobAttack(source), amount);
    }
    public static void damageByRatio(LivingEntity target, EntityMaid source, float ratio) {
        target.hurt(source.damageSources().mobAttack(source), target.getMaxHealth() * ratio);
    }
    public static void damagePercent(LivingEntity target, EntityMaid source, float percent) {
        target.hurt(source.damageSources().mobAttack(source), target.getMaxHealth() * percent / 100f);
    }
    public static void magicDamage(LivingEntity target, EntityMaid source, float amount) {
        target.hurt(source.damageSources().magic(), amount);
    }
    public static void genericDamage(LivingEntity target, EntityMaid source, float amount) {
        target.hurt(source.damageSources().generic(), amount);
    }
    public static void trueDamage(LivingEntity target, EntityMaid source, float amount) {
        target.hurt(source.damageSources().genericKill(), amount);
    }
    public static void executionKill(LivingEntity target, EntityMaid source) {
        target.hurt(source.damageSources().genericKill(), target.getHealth() + 1);
    }
    public static void bleed(LivingEntity target, float damagePerTick, int ticks) {
        target.getPersistentData().putFloat("lma_bleed_dmg", damagePerTick);
        target.getPersistentData().putInt("lma_bleed_ticks", ticks);
    }
    public static void damageNearby(EntityMaid center, float range, float damage, boolean hostileOnly) {
        var list = center.level().getEntitiesOfClass(LivingEntity.class,
            center.getBoundingBox().inflate(range), e -> e != center && e.isAlive() && (!hostileOnly || e instanceof net.minecraft.world.entity.monster.Enemy));
        for (var e : list) e.hurt(center.damageSources().mobAttack(center), damage);
    }

    // === 治疗 ===
    public static void heal(LivingEntity target, float amount) { target.heal(amount); }
    public static void healByRatio(LivingEntity target, float ratio) {
        target.heal(target.getMaxHealth() * ratio);
    }
    public static void healPercent(LivingEntity target, float percent) {
        target.heal(target.getMaxHealth() * percent / 100f);
    }

    // === 控制 ===
    public static void knockback(LivingEntity target, EntityMaid source, float strength) {
        Vec3 dir = target.position().subtract(source.position()).normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(strength)));
    }
    public static void knockbackWithVertical(LivingEntity target, EntityMaid source, float strength, float vertical) {
        Vec3 dir = target.position().subtract(source.position()).normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(dir.x * strength, vertical, dir.z * strength));
    }
    public static void launch(LivingEntity target, EntityMaid source, float horizontal, float vertical) {
        Vec3 dir = target.position().subtract(source.position()).normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(dir.x * horizontal, vertical, dir.z * horizontal));
    }
    public static void push(LivingEntity target, EntityMaid source, float strength) {
        Vec3 dir = target.position().subtract(source.position()).normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(strength)));
    }
    public static void pull(LivingEntity target, EntityMaid source, float strength) {
        Vec3 dir = target.position().vectorTo(source.position()).normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(strength)));
    }

    // === 状态 ===
    public static void setFire(LivingEntity target, int seconds) { target.setSecondsOnFire(seconds); }
    public static void extinguish(LivingEntity target) { target.clearFire(); }
    public static void shieldEffect(LivingEntity target, int amount, int duration) {
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, Math.max(0, (amount / 4) - 1)));
    }
    public static void shield(EntityMaid maid, float absorb) {
        maid.setAbsorptionAmount(maid.getAbsorptionAmount() + absorb);
    }

    // === 复合 ===
    public static void lifeSteal(LivingEntity target, EntityMaid source, float damage, float healRatio) {
        target.hurt(source.damageSources().mobAttack(source), damage);
        source.heal(damage * healRatio);
    }

    // === 弹射物 ===
    public static void launchProjectile(EntityMaid source, LivingEntity target, String projectileId, float speed, float inaccuracy) {
        var rl = ResourceLocation.tryParse(projectileId);
        if (rl == null) return;
        var type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (type == null) return;
        var entity = type.create(source.level());
        if (!(entity instanceof Projectile pr)) return;
        pr.setPos(source.getX(), source.getEyeY(), source.getZ());
        var dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(pr.position()).normalize();
        pr.shoot(dir.x, dir.y, dir.z, speed, inaccuracy);
        pr.setOwner(source);
        source.level().addFreshEntity(pr);
    }

    // === 近战 ===
    public static void doHurtTarget(EntityMaid maid, LivingEntity target) { maid.doHurtTarget(target); }

    // === 怪物图鉴 (v35.4) ===
    private static final int KILL_BONUS_THRESHOLD = 1000;

    public static void ownerKillBonusDamage(LivingEntity target, EntityMaid source, float amount) {
        int kills = 0;
        if (source.getOwner() instanceof net.minecraft.server.level.ServerPlayer sp)
            kills = sp.getStats().getValue(Stats.ENTITY_KILLED, target.getType());
        if (kills >= KILL_BONUS_THRESHOLD)
            target.hurt(source.damageSources().mobAttack(source), amount);
    }
}
