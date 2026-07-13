package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.furnace;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ItemResolver;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.search.BlockSearch;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.block.FurnaceOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * 熔炉物品管理 — 委托 SmeltItemAction。
 * <p>流程：搜索熔炉 → 检查输入兼容 → 加物品 → 加燃料。
 */
public final class SmeltExecute {
    private SmeltExecute() {}

    /**
     * @param inputItemId 要烧炼的物品 ID
     * @param fuelItemId  燃料物品 ID（可选，空字符串表示自动查找）
     * @param range       搜索范围
     */
    public static boolean execute(ServerLevel world, EntityMaid maid, String inputItemId, String fuelItemId, int range) {
        Item toSmelt = ItemResolver.resolve(inputItemId);
        if (toSmelt == null) return false;
        Item fuel = fuelItemId.isEmpty() ? null : ItemResolver.resolve(fuelItemId);

        var results = BlockSearch.findBlocks(world, maid.blockPosition(), range, 4,
            (pos, state) -> world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity);
        if (results.isEmpty()) return false;

        var te = world.getBlockEntity(results.get(0).pos());
        if (!(te instanceof AbstractFurnaceBlockEntity furnace)) return false;
        var handler = te.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) return false;

        var inv = maid.getAvailableInv(false);

        // 1. 加输入物品
        var inputSlot = handler.getStackInSlot(0);
        if (inputSlot.isEmpty() || inputSlot.is(toSmelt)) {
            for (int i = 0; i < inv.getSlots(); i++) {
                var stack = inv.getStackInSlot(i);
                if (stack.is(toSmelt)) {
                    var taken = inv.extractItem(i, 1, false);
                    var leftover = handler.insertItem(0, taken, false);
                    if (!leftover.isEmpty()) inv.insertItem(i, leftover, false);
                    else break;
                }
            }
        }

        // 2. 加燃料（跳过输入物品本身）
        var fuelSlot = handler.getStackInSlot(1);
        boolean needFuel = fuelSlot.isEmpty() || fuelSlot.getCount() < 4;
        if (needFuel) {
            for (int i = 0; i < inv.getSlots(); i++) {
                var stack = inv.getStackInSlot(i);
                boolean isFuel = stack.getBurnTime(RecipeType.SMELTING) > 0;
                if (!isFuel || (fuel != null && !stack.is(fuel))) continue;
                if (toSmelt != null && stack.is(toSmelt)) continue; // 跳过输入物品
                var taken = inv.extractItem(i, 1, false);
                var leftover = handler.insertItem(1, taken, false);
                if (!leftover.isEmpty()) inv.insertItem(i, leftover, false);
                else break;
            }
        }

        maid.getPersistentData().putLong("lma_flow_tick", world.getGameTime());
        return true;
    }
}
