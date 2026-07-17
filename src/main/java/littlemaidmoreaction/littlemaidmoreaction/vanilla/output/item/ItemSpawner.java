package littlemaidmoreaction.littlemaidmoreaction.vanilla.output.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** 物品实体生成 — 纯命令, 有副作用。 */
public final class ItemSpawner {
    private ItemSpawner() {}

    /** 生成立即可拾取的物品实体 (setPickUpDelay=0, TLM自动捡入背包) */
    public static void spawnForPickup(EntityMaid maid, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemEntity entity = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.5, maid.getZ(), stack);
        entity.setPickUpDelay(0);
        maid.level().addFreshEntity(entity);
    }

    /** 检查合成剩余物并生成 (hasCraftingRemainingItem → 空桶/空瓶/空碗等) */
    public static void spawnRemainingIfAny(EntityMaid maid, ItemStack consumed) {
        if (consumed == null || consumed.isEmpty()) return;
        if (consumed.getItem().hasCraftingRemainingItem()) {
            ItemStack remainder = consumed.getItem().getCraftingRemainingItem().getDefaultInstance();
            remainder.setCount(consumed.getCount());
            spawnForPickup(maid, remainder);
        }
    }
}
