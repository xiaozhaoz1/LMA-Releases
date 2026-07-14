package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.block;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.item.ItemSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraftforge.items.ItemHandlerHelper;

/** 唱片机方块输出 — 纯命令, 有副作用。 */
public final class JukeboxOutput {
    private JukeboxOutput() {}

    /** 向唱片机插入唱片 */
    public static boolean insertDisc(JukeboxBlockEntity jukebox, ItemStack disc, Level level, BlockPos pos) {
        if (disc.isEmpty()) return false;
        jukebox.setFirstItem(disc.copy());
        jukebox.setChanged();
        level.levelEvent(null, 1010, pos, Item.getId(disc.getItem()));
        return true;
    }

    /** 从唱片机弹出唱片回女仆背包。背包满时溢出掉地上。 */
    public static boolean ejectDisc(JukeboxBlockEntity jukebox, EntityMaid maid) {
        ItemStack record = jukebox.getFirstItem();
        if (record.isEmpty()) return false;
        ItemStack remainder = ItemHandlerHelper.insertItem(maid.getAvailableInv(true), record.copy(), false);
        if (remainder.getCount() < record.getCount()) {
            jukebox.removeItem(0, 1);
            jukebox.setChanged();
            if (!remainder.isEmpty()) ItemSpawner.spawnForPickup(maid, remainder);
            return true;
        }
        return false;
    }
}
