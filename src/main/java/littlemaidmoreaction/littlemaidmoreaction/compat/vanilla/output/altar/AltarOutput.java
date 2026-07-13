package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.altar;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitRecipes;
import com.github.tartaricacid.touhoulittlemaid.inventory.AltarRecipeInventory;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityAltar;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 祭坛操作原语 */
public final class AltarOutput {
    private AltarOutput() {}

    /** 向祭坛槽位放置物品，返回放置数量 */
    public static int placeItems(TileEntityAltar ref, EntityMaid maid, ItemStack item) {
        List<BlockPos> slotPositions = ref.getCanPlaceItemPosList().getData();
        int placed = 0;
        for (BlockPos pos : slotPositions) {
            if (item.isEmpty()) break;
            if (maid.level().getBlockEntity(pos) instanceof TileEntityAltar altar
                    && altar.isCanPlaceItem()
                    && altar.handler.getStackInSlot(0).isEmpty()) {
                ItemStack one = item.split(1);
                altar.handler.setStackInSlot(0, one);
                altar.refresh();
                placed++;
            }
        }
        return placed;
    }

    /** 检查并触发祭坛合成 — 所有槽满→匹配配方→生成输出→清空槽位 */
    public static boolean tryTriggerCraft(EntityMaid maid, TileEntityAltar ref) {
        List<BlockPos> slotPositions = ref.getCanPlaceItemPosList().getData();
        if (slotPositions.isEmpty()) return false;

        var items = new ArrayList<ItemStack>();
        for (BlockPos pos : slotPositions) {
            if (maid.level().getBlockEntity(pos) instanceof TileEntityAltar altar && altar.isCanPlaceItem()) {
                ItemStack stack = altar.handler.getStackInSlot(0);
                items.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
        }
        if (items.stream().anyMatch(ItemStack::isEmpty)) return false;

        var inv = new AltarRecipeInventory();
        for (int i = 0; i < Math.min(items.size(), 6); i++) inv.setItem(i, items.get(i));

        var recipes = maid.level().getRecipeManager().getRecipesFor(InitRecipes.ALTAR_CRAFTING, inv, maid.level());
        if (recipes.isEmpty()) return false;

        var recipe = recipes.get(0);
        BlockPos center = getCenterPos(slotPositions, ref.getBlockPos());
        if (maid.level() instanceof ServerLevel sl)
            recipe.spawnOutputEntity(sl, center.above(2), inv);

        for (BlockPos pos : slotPositions) {
            if (maid.level().getBlockEntity(pos) instanceof TileEntityAltar altar) {
                altar.handler.setStackInSlot(0, ItemStack.EMPTY);
                altar.refresh();
            }
        }
        return true;
    }

    private static BlockPos getCenterPos(List<BlockPos> posList, BlockPos refPos) {
        int x = 0, z = 0, y = refPos.getY() - 2;
        for (BlockPos pos : posList) {
            if (pos.getY() == y) { x += pos.getX(); z += pos.getZ(); }
        }
        return new BlockPos(x / 8, y, z / 8);
    }
}
