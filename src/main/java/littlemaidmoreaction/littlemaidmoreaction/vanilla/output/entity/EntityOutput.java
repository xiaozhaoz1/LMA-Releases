package littlemaidmoreaction.littlemaidmoreaction.vanilla.output.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** 通用实体输出原语 — 不限于女仆 */
public final class EntityOutput {
    private EntityOutput() {}

    /** 对实体造成伤害 */
    public static void hurt(LivingEntity target, DamageSource source, float amount) {
        target.hurt(source, amount);
    }
    /** 杀死实体 */
    public static void kill(LivingEntity target) {
        target.kill();
    }
    /** 设置实体位置（不触发传送事件） */
    public static void setPos(Entity entity, double x, double y, double z) {
        entity.setPos(x, y, z);
    }
    /** 设置实体位置（方块坐标中心） */
    public static void setPos(Entity entity, BlockPos pos) {
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
}
