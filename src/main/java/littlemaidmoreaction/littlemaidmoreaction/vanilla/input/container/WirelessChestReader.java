package littlemaidmoreaction.littlemaidmoreaction.vanilla.input.container;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemWirelessIO;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.LinkedHashMap;
import java.util.Map;

/** 隙间箱子物品读取 — 纯查询, 无副作用 */
public final class WirelessChestReader {
    private WirelessChestReader() {}

    public static Map<Item, Integer> readAll(EntityMaid maid) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        var baubleHandler = maid.getMaidBauble();
        for (int i = 0; i < baubleHandler.getSlots(); i++) {
            ItemStack bauble = baubleHandler.getStackInSlot(i);
            if (bauble.isEmpty()) continue;
            var bindingPos = ItemWirelessIO.getBindingPos(bauble);
            if (bindingPos == null) continue;
            BlockEntity be = maid.level().getBlockEntity(bindingPos);
            if (be == null) continue;
            be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                for (int j = 0; j < handler.getSlots(); j++) {
                    ItemStack s = handler.getStackInSlot(j);
                    if (!s.isEmpty()) result.merge(s.getItem(), s.getCount(), Integer::sum);
                }
            });
        }
        return result;
    }
}
