package littlemaidmoreaction.littlemaidmoreaction.adapter.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Optional;

/**
 * TLM API 统一封装 — 所有与 TLM 主模组的交互经由此类。
 *
 * <p>目的：将 TLM 的直接依赖隔离在 adapter/tlm 包中，
 * 核心引擎和动作实现通过此 API 间接访问 TLM 功能。
 */
public final class MaidAPI {

    /** 查找女仆任务 */
    public static Optional<IMaidTask> findTask(String taskId) {
        return TaskManager.findTask(ResourceLocation.fromNamespaceAndPath("touhou_little_maid", taskId));
    }

    /** 获取女仆背包 */
    public static ItemStackHandler getInventory(EntityMaid maid) {
        return maid.getMaidInv();
    }

    /** 设置女仆好感度为最大值 */
    public static void setFavorMax(EntityMaid maid) {
        maid.getFavorabilityManager().max();
    }

    /** 获取女仆好感度 */
    public static int getFavorability(EntityMaid maid) {
        return maid.getFavorability();
    }

    /** 设置女仆好感度 */
    public static void setFavorability(EntityMaid maid, int value) {
        maid.setFavorability(value);
    }

    /** 设置女仆任务 */
    public static void setTask(EntityMaid maid, IMaidTask task) {
        maid.setTask(task);
    }

    /** 设置女仆目标 */
    public static void setTarget(EntityMaid maid, net.minecraft.world.entity.LivingEntity target) {
        maid.setTarget(target);
    }

    /** 设置女仆 home 模式 */
    public static void setHomeMode(EntityMaid maid, boolean enabled) {
        maid.setHomeModeEnable(enabled);
    }

    /** 清除女仆限制 */
    public static void clearRestriction(EntityMaid maid) {
        maid.clearRestriction();
    }

    /** 设置女仆坐下状态 */
    public static void setSitting(EntityMaid maid, boolean sitting) {
        maid.setInSittingPose(sitting);
    }

    /** 设置女仆拾取模式 */
    public static void setPickup(EntityMaid maid, boolean pickup) {
        maid.setPickup(pickup);
    }

    /** 设置女仆饥饿值 */
    public static void setHunger(EntityMaid maid, int hunger) {
        maid.setHunger(hunger);
    }

    /** 设置女仆无敌状态 */
    public static void setInvulnerable(EntityMaid maid, boolean invulnerable) {
        maid.setEntityInvulnerable(invulnerable);
    }

    /** 设置女仆模型 ID */
    public static void setModelId(EntityMaid maid, String modelId) {
        maid.setModelId(modelId);
    }

    /** 获取女仆主人的 UUID */
    public static java.util.UUID getOwnerUuid(EntityMaid maid) {
        return maid.getOwnerUUID();
    }

    /** 检查实体是否为女仆的主人 */
    public static boolean isOwnedBy(EntityMaid maid, net.minecraft.world.entity.LivingEntity entity) {
        return maid.isOwnedBy(entity);
    }

    private MaidAPI() {}
}
