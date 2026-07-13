package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.target;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** 目标状态读取 — 覆盖 ~22 个 {@code impl/condition/target/} 查询 */
public final class TargetStateReader {
    private TargetStateReader() {}

    public static double getDistance(EntityMaid m, LivingEntity t) { return m.distanceTo(t); }
    public static double getHorizontalDistance(EntityMaid m, LivingEntity t) {
        double dx = m.getX() - t.getX(); double dz = m.getZ() - t.getZ(); return Math.sqrt(dx*dx + dz*dz);
    }
    public static double getVerticalDistance(EntityMaid m, LivingEntity t) { return Math.abs(m.getY() - t.getY()); }
    public static float getHealth(LivingEntity t) { return t.getHealth(); }
    public static float getHealthRatio(LivingEntity t) { return t.getHealth() / t.getMaxHealth(); }
    public static float getMaxHealth(LivingEntity t) { return t.getMaxHealth(); }
    public static float getArmor(LivingEntity t) { return t.getArmorValue(); }
    public static String getName(LivingEntity t) { return t.getName().getString(); }
    public static String getTypeId(LivingEntity t) { return ForgeRegistries.ENTITY_TYPES.getKey(t.getType()).toString(); }

    public static boolean isAlive(LivingEntity t) { return t.isAlive(); }
    public static boolean isBaby(LivingEntity t) { return t.isBaby(); }
    public static boolean isBoss(LivingEntity t) { return t instanceof EnderDragon || t instanceof WitherBoss || !t.getType().getCategory().isFriendly(); }
    public static boolean isMonster(LivingEntity t) { return t instanceof Enemy; }
    public static boolean isAnimal(LivingEntity t) { return t instanceof Animal; }
    public static boolean isUndead(LivingEntity t) { return t.isInvertedHealAndHarm(); }
    public static boolean isPlayer(LivingEntity t) { return t instanceof Player; }
    public static boolean isBurning(LivingEntity t) { return t.isOnFire(); }
    public static boolean isOnGround(LivingEntity t) { return t.onGround(); }
    public static boolean canSee(EntityMaid m, LivingEntity t) { return m.getSensing().hasLineOfSight(t); }
    public static boolean wouldLethal(EntityMaid m, LivingEntity t, float damage) { return t.getHealth() <= damage; }
    public static boolean hasEffect(LivingEntity t, MobEffect effect) { return t.hasEffect(effect); }

    public static ItemStack getHoldingItem(LivingEntity t) { return t.getMainHandItem(); }
    public static String getHoldingItemId(LivingEntity t) {
        var s = t.getMainHandItem(); return s.isEmpty() ? "air" : ForgeRegistries.ITEMS.getKey(s.getItem()).toString();
    }

    // === Phase 7: 位置编目 ===
    /** 目标精确坐标 */
    public static Vec3 getPosition(LivingEntity t) { return t.position(); }
    /** 目标脚下方块坐标 */
    public static BlockPos getBlockPos(LivingEntity t) { return t.blockPosition(); }
}
