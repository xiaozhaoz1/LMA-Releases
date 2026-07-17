package littlemaidmoreaction.littlemaidmoreaction.vanilla.output.movement;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

/** 移动输出 — 瞬移/冲刺/跳跃/推动 */
public final class MovementOutput {
    private MovementOutput() {}

    public static void teleport(Entity entity, BlockPos pos) {
        entity.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
    public static void teleportOffset(Entity entity, LivingEntity relative, String dir, double dist) {
        Vec3 look = relative.getLookAngle();
        Vec3 offset = switch (dir) {
            case "in_front" -> look.scale(dist);
            case "behind" -> look.scale(-dist);
            case "left" -> new Vec3(-look.z, 0, look.x).normalize().scale(dist);
            case "right" -> new Vec3(look.z, 0, -look.x).normalize().scale(dist);
            default -> Vec3.ZERO;
        };
        entity.teleportTo(relative.getX() + offset.x, relative.getY(), relative.getZ() + offset.z);
    }
    public static void dash(EntityMaid maid, double speed) {
        Vec3 look = maid.getLookAngle();
        maid.setDeltaMovement(look.x * speed, look.y * speed * 0.3, look.z * speed);
    }
    public static void leap(EntityMaid maid, double h, double v) {
        Vec3 look = maid.getLookAngle();
        maid.setDeltaMovement(look.x * h, v, look.z * h);
    }
    public static void setMotion(Entity entity, double x, double y, double z) {
        entity.setDeltaMovement(x, y, z);
    }
    public static void faceTarget(EntityMaid maid, LivingEntity target) {
        BehaviorUtils.lookAtEntity(maid, target);
    }
    public static void swapPosition(EntityMaid maid, Entity other) {
        Vec3 mp = maid.position(); maid.teleportTo(other.getX(), other.getY(), other.getZ());
        other.teleportTo(mp.x, mp.y, mp.z);
    }
    public static void slow(LivingEntity target, int ticks, int amp) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, amp));
    }
    public static void freezeAi(EntityMaid maid, boolean frozen) {
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
            frozen ? null : maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null));
    }
    /** 冻结目标AI — 写入 IS_PANICKING + PersistentData 持续扣减 */
    public static void freezeAi(LivingEntity target, int ticks) {
        target.getBrain().setMemory(MemoryModuleType.IS_PANICKING, true);
        target.getPersistentData().putInt("lma_freeze_ticks", ticks);
    }
    public static void followOwner(EntityMaid maid) {
        var o = maid.getOwner(); if (o == null) return;
        BehaviorUtils.setWalkAndLookTargetMemories(maid, o.blockPosition(), 1.2f, 3);
    }
    public static void guardPos(EntityMaid maid, BlockPos pos) { maid.restrictTo(pos, 10); }
    public static void guardPos(EntityMaid maid, BlockPos pos, int distance) { maid.restrictTo(pos, distance); }

    // === Phase 4: 目标朝向移动 ===
    /** 朝目标冲刺 — 提取自 DashAction toward_target 逻辑 */
    public static void dashToward(EntityMaid maid, LivingEntity target, double distance, double speedMult) {
        var dir = target.position().subtract(maid.position());
        double h = dir.horizontalDistance();
        if (h > 0.001) dir = new Vec3(dir.x / h, 0, dir.z / h);
        else dir = maid.getLookAngle();
        maid.setDeltaMovement(dir.x * distance * speedMult, 0.2, dir.z * distance * speedMult);
        maid.hurtMarked = true;
    }
    /** 朝目标跳跃 — 提取自 LeapAction 逻辑 */
    public static void leapToward(EntityMaid maid, LivingEntity target, double hPower, double vPower) {
        var dir = target.position().subtract(maid.position());
        double dist = dir.horizontalDistance();
        var d = dist > 0.001 ? new Vec3(dir.x / dist, 0, dir.z / dist) : maid.getLookAngle();
        maid.setDeltaMovement(d.x * hPower, vPower, d.z * hPower);
        maid.hurtMarked = true;
    }
    /** 多模式瞬移 — 提取自 TeleportAction */
    public static void teleportWithMode(Entity entity, LivingEntity relative, String mode, double distance, double sideOffset) {
        double x, y, z;
        var look = relative.getLookAngle();
        switch (mode) {
            case "in_front" -> { x = relative.getX() + look.x * distance; y = relative.getY(); z = relative.getZ() + look.z * distance; }
            case "behind"  -> { x = relative.getX() - look.x * distance; y = relative.getY(); z = relative.getZ() - look.z * distance; }
            case "side" -> {
                var s = new Vec3(-look.z, 0, look.x).normalize().scale(sideOffset);
                x = relative.getX() + s.x; y = relative.getY(); z = relative.getZ() + s.z;
            }
            default -> { x = entity.getX(); y = entity.getY(); z = entity.getZ(); }
        }
        entity.teleportTo(x, y, z);
    }
}
