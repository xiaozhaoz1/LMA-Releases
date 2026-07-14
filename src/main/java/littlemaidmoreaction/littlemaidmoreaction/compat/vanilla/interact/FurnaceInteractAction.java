package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.SlotLayout;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/** v30: SlotLayout 替代硬编码槽位 */
@RuleAction
public class FurnaceInteractAction extends AbstractFunctionalBlockInteraction {

    private static final SlotLayout SLOTS = SlotLayout.FURNACE;

    @Override public String id() { return "furnace_interact"; }
    @Override public String displayName() { return "熔炉互动"; }
    @Override protected String defaultBlockId() { return "minecraft:furnace"; }

    @Override
    public java.util.List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() {
        var list = new java.util.ArrayList<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>>();
        list.addAll(super.params());
        list.add(new littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam.StringParam("fuel_item", "燃料ID(可选，默认自动)", ""));
        return java.util.List.copyOf(list);
    }

    @Override
    protected java.util.List<String> validActions() { return java.util.List.of(); }

    @Override
    protected void doInteract(BlockPos pos, BlockState state, EntityMaid maid,
                               Map<String, String> params, String action) {
        var furnaceOpt = getBlockEntity(maid.level(), pos, AbstractFurnaceBlockEntity.class);
        if (furnaceOpt.isEmpty()) return;
        AbstractFurnaceBlockEntity furnace = furnaceOpt.get();

        String itemId = params.getOrDefault("item_id", "");
        String fuelId = params.getOrDefault("fuel_item", "");

        // 1. 取产物
        ItemStack result = furnace.getItem(SLOTS.slot("output"));
        if (!result.isEmpty()) {
            furnace.setItem(SLOTS.slot("output"), ItemStack.EMPTY);
            spawnPickup(maid, result);
            furnace.setChanged();
            completeFlowTask(maid);
            return;
        }

        // 2. 加材料
        ItemStack input = furnace.getItem(SLOTS.slot("input"));
        if (!itemId.isEmpty()) {
            var ti = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
            if (ti != null) {
                int space = input.isEmpty() ? Math.min(8, ti.getDefaultInstance().getMaxStackSize())
                    : Math.max(0, Math.min(8, input.getMaxStackSize() - input.getCount()));
                if (input.isEmpty() || space > 0) {
                    ItemStack mat = extractFromMaidAndWirelessChest(maid,
                        s -> s.is(ti) && !AbstractFurnaceBlockEntity.isFuel(s), space);
                    if (!mat.isEmpty()) {
                        if (input.isEmpty()) furnace.setItem(SLOTS.slot("input"), mat.copy());
                        else { input.grow(mat.getCount()); }
                        furnace.setChanged();
                    }
                }
            }
        }

        // 3. 加燃料
        ItemStack fuel = furnace.getItem(SLOTS.slot("fuel"));
        if (fuel.isEmpty() || fuel.getCount() < fuel.getMaxStackSize()) {
            int space = fuel.isEmpty() ? 64 : fuel.getMaxStackSize() - fuel.getCount();
            java.util.function.Predicate<ItemStack> fuelFilter;
            if (!fuelId.isEmpty()) {
                var fi = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(fuelId));
                fuelFilter = fi != null ? s -> s.is(fi) : mkFuelFilter(itemId);
            } else {
                fuelFilter = mkFuelFilter(itemId);
            }
            ItemStack f = extractFromMaidAndWirelessChest(maid, fuelFilter, space);
            if (!f.isEmpty()) {
                if (fuel.isEmpty()) furnace.setItem(SLOTS.slot("fuel"), f.copy());
                else fuel.grow(f.getCount());
                furnace.setChanged();
            }
        }

        playInteractionFeedback(maid, pos, SoundEvents.FURNACE_FIRE_CRACKLE);
    }

    private static java.util.function.Predicate<ItemStack> mkFuelFilter(String itemId) {
        return s -> {
            if (!AbstractFurnaceBlockEntity.isFuel(s)) return false;
            if (itemId.isEmpty()) return true;
            var ti = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
            return ti == null || !s.is(ti);
        };
    }

    private static void spawnPickup(EntityMaid maid, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity entity = new ItemEntity(maid.level(),
                maid.getX(), maid.getY() + 0.5, maid.getZ(), stack);
        entity.setPickUpDelay(0);
        maid.level().addFreshEntity(entity);
    }
}
