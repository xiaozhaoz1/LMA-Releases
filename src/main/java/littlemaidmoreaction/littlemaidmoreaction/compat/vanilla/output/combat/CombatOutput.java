package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** 战斗输出 — 伤害/治疗/击退/状态/护盾 */
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
    /** 流血 — PersistentData 持续扣血，由 BleedHandler tick 处理 */
    public static void bleed(LivingEntity target, float damagePerTick, int ticks) {
        target.getPersistentData().putFloat("lma_bleed_dmg", damagePerTick);
        target.getPersistentData().putInt("lma_bleed_ticks", ticks);
    }
    /** 范围伤害 — hostileOnly 仅伤害敌对生物 */
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

    // === 击退/位移 ===
    public static void knockback(LivingEntity target, EntityMaid source, float strength) {
        double dx = target.getX() - source.getX();
        double dz = target.getZ() - source.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len == 0) return;
        target.knockback(strength * 0.5, dx / len, dz / len);
    }
    public static void knockbackWithVertical(LivingEntity target, EntityMaid source, float strength, float vertical) {
        double dx = target.getX() - source.getX(), dz = target.getZ() - source.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.001) { dx /= len; dz /= len; }
        target.knockback(strength, dx, dz);
        target.setDeltaMovement(target.getDeltaMovement().add(0, vertical, 0));
    }
    /** 击飞 — 从 source 朝 target 方向施加水平+垂直速度 */
    public static void launch(LivingEntity target, EntityMaid source, float horizontal, float vertical) {
        var dir = target.position().subtract(source.position()).normalize();
        target.setDeltaMovement(dir.x * horizontal, vertical, dir.z * horizontal);
        target.hurtMarked = true;
    }
    public static void push(LivingEntity target, EntityMaid source, float strength) {
        Vec3 dir = source.position().vectorTo(target.position()).normalize();
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
    /** 发射弹射物 — 朝 target 方向 shoot */
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
}
