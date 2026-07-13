package littlemaidmoreaction.littlemaidmoreaction.task.service;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class OutputCollector {
    private static final System.Logger LOG = System.getLogger("LMA-V16-OutputCollector");
    private OutputCollector() {}

    public static void spawnForPickup(EntityMaid maid, ItemStack stack) {
        if (stack.isEmpty()) return;
        Level level = maid.level();
        LOG.log(System.Logger.Level.INFO, "[V16] [OutputCollector] spawnForPickup: {0} x{1} at ({2},{3},{4})",
                stack.getItem(), stack.getCount(), maid.getX(), maid.getY(), maid.getZ());
        ItemEntity entity = new ItemEntity(level, maid.getX(), maid.getY() + 0.5, maid.getZ(), stack.copy());
        entity.setPickUpDelay(0);
        level.addFreshEntity(entity);
        stack.setCount(0);
    }
}
