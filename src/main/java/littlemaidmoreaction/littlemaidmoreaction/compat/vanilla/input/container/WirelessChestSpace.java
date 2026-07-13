package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.container;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * 隙间箱子空间计算。
 * <p>getBindingPos() 用于排除重复扫描 (未来 NearbyContainerSpace 使用)。
 */
public final class WirelessChestSpace {
    private WirelessChestSpace() {}

    /** 隙间绑定箱子的位置 (null=未绑定, 用于排除重复扫描) */
    @Nullable
    public static BlockPos getBindingPos(EntityMaid maid) {
        var baubleInv = maid.getMaidBauble();
        for (int i = 0; i < baubleInv.getSlots(); i++) {
            ItemStack bauble = baubleInv.getStackInSlot(i);
            if (bauble.isEmpty()) continue;
            BlockPos pos = ItemWirelessIO.getBindingPos(bauble);
            if (pos != null) return pos;
        }
        return null;
    }

    /** 隙间箱子对指定物品的剩余空间 */
    public static int calculate(EntityMaid maid, ItemStack sample) {
        IItemHandler handler = getWirelessHandler(maid);
        if (handler == null) return 0;
        if (sample == null || sample.isEmpty()) return 0;
        int maxStack = sample.getMaxStackSize();
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                total += maxStack;
            } else if (ItemStack.isSameItemSameTags(stack, sample)) {
                total += Math.max(0, maxStack - stack.getCount());
            }
        }
        return total;
    }

    @Nullable
    private static IItemHandler getWirelessHandler(EntityMaid maid) {
        var baubleInv = maid.getMaidBauble();
        for (int i = 0; i < baubleInv.getSlots(); i++) {
            ItemStack bauble = baubleInv.getStackInSlot(i);
            if (bauble.isEmpty()) continue;
            BlockPos bindingPos = ItemWirelessIO.getBindingPos(bauble);
            if (bindingPos == null) continue;
            float maxDist = maid.getRestrictRadius();
            if (maid.distanceToSqr(bindingPos.getX(), bindingPos.getY(), bindingPos.getZ()) > maxDist * maxDist) continue;
            BlockEntity be = maid.level().getBlockEntity(bindingPos);
            if (be == null) continue;
            var handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve();
            if (handler.isPresent()) return handler.get();
        }
        return null;
    }
}
