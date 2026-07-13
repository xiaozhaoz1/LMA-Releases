package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.brew.BrewingRecipeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 女仆炼药台互动 (v12.4 — 炼药配方抽象层 + 视觉反馈 + 任务完成)。
 *
 * <p>通过 {@link BrewingRecipeRegistry} 查询炼药配方，
 * 支持原版和模组自定义配方（通过 {@code IBrewingRecipeProvider} 扩展）。
 * 女仆走到炼药台旁 → 挥臂添加燃料/水瓶/材料/取药水 → 标记完成。
 *
 * @deprecated v14已删除炼药任务，此类仅保留供参考，不再注册为规则动作。
 */
@Deprecated
public class BrewingStandInteractAction extends AbstractFunctionalBlockInteraction {

    private static final int[] BOTTLE_SLOTS = {0, 1, 2};
    private static final int SLOT_INGREDIENT = 3;
    private static final int SLOT_FUEL = 4;

    @Override
    public String id() { return "brewing_interact"; }

    @Override
    public String displayName() { return "炼药台互动"; }

    @Override
    protected String defaultBlockId() { return "minecraft:brewing_stand"; }

    @Override
    protected List<String> validActions() {
        return List.of("add_fuel", "add_bottles", "add_ingredient", "take_result");
    }

    @Override
    protected void doInteract(BlockPos pos, BlockState state, EntityMaid maid,
                               Map<String, String> params, String action) {
        var brewingOpt = getBlockEntity(maid.level(), pos, BrewingStandBlockEntity.class);
        if (brewingOpt.isEmpty()) return;
        BrewingStandBlockEntity brewing = brewingOpt.get();

        boolean didSomething = switch (action) {
            case "add_fuel"       -> addFuel(brewing, maid);
            case "add_bottles"    -> addBottles(brewing, maid);
            case "add_ingredient" -> addIngredient(brewing, maid, params);
            case "take_result"    -> takeResult(brewing, maid);
            default -> false;
        };

        if (didSomething) {
            playInteractionFeedback(maid, pos, SoundEvents.BREWING_STAND_BREW);
            completeFlowTask(maid);
        }
    }

    private boolean addFuel(BrewingStandBlockEntity brewing, EntityMaid maid) {
        ItemStack fuelSlot = brewing.getItem(SLOT_FUEL);
        if (!fuelSlot.isEmpty() && fuelSlot.getCount() >= fuelSlot.getMaxStackSize())
            return false;
        return transferMaidToSlot(maid, brewing, SLOT_FUEL,
            s -> s.is(Items.BLAZE_POWDER));
    }

    private boolean addBottles(BrewingStandBlockEntity brewing, EntityMaid maid) {
        for (int slot : BOTTLE_SLOTS) {
            if (!brewing.getItem(slot).isEmpty()) continue;
            if (transferMaidToSlot(maid, brewing, slot,
                s -> BrewingRecipeRegistry.isValidBase(s)
                    && s.is(Items.POTION)
                    && PotionUtils.getPotion(s) == Potions.WATER)) {
                return true;
            }
        }
        return false;
    }

    private boolean addIngredient(BrewingStandBlockEntity brewing, EntityMaid maid,
                                  Map<String, String> params) {
        if (!brewing.getItem(SLOT_INGREDIENT).isEmpty()) return false;
        if (!hasAnyBottle(brewing)) return false;

        Predicate<ItemStack> filter;
        var specifiedItem = parseItemId(params);
        if (specifiedItem.isPresent()) {
            filter = s -> s.is(specifiedItem.get())
                && BrewingRecipeRegistry.isIngredient(s);
        } else {
            filter = s -> BrewingRecipeRegistry.isIngredient(s)
                && matchesAnyBottle(brewing, s);
        }
        return transferMaidToSlot(maid, brewing, SLOT_INGREDIENT, filter);
    }

    private boolean takeResult(BrewingStandBlockEntity brewing, EntityMaid maid) {
        var handlerOpt = getItemHandler(brewing);
        if (handlerOpt.isEmpty()) return false;
        IItemHandler brewInv = handlerOpt.get();

        for (int slot : BOTTLE_SLOTS) {
            ItemStack potion = brewing.getItem(slot);
            if (potion.isEmpty()) continue;
            if (potion.is(Items.POTION)
                && PotionUtils.getPotion(potion) == Potions.WATER) continue;
            if (transferBlockToMaid(maid, brewInv, slot, potion.getCount())) {
                brewing.setChanged();
                return true;
            }
        }
        return false;
    }

    private boolean transferMaidToSlot(EntityMaid maid, BrewingStandBlockEntity brewing,
                                        int slot, Predicate<ItemStack> filter) {
        ItemStack extracted = extractFromMaidInv(maid, filter, 1);
        if (extracted.isEmpty()) return false;

        ItemStack existing = brewing.getItem(slot);
        if (existing.isEmpty()) {
            brewing.setItem(slot, extracted);
        } else if (ItemStack.isSameItemSameTags(existing, extracted)) {
            int space = existing.getMaxStackSize() - existing.getCount();
            int toAdd = Math.min(space, extracted.getCount());
            existing.grow(toAdd);
            extracted.shrink(toAdd);
            if (!extracted.isEmpty()) {
                var maidInv = getMaidInventory(maid);
                for (int i = 0; i < maidInv.getSlots(); i++) {
                    extracted = maidInv.insertItem(i, extracted, false);
                    if (extracted.isEmpty()) break;
                }
            }
        } else {
            var maidInv = getMaidInventory(maid);
            for (int i = 0; i < maidInv.getSlots(); i++) {
                extracted = maidInv.insertItem(i, extracted, false);
                if (extracted.isEmpty()) break;
            }
            return false;
        }
        brewing.setChanged();
        return true;
    }

    private boolean hasAnyBottle(BrewingStandBlockEntity brewing) {
        for (int slot : BOTTLE_SLOTS) {
            if (!brewing.getItem(slot).isEmpty()) return true;
        }
        return false;
    }

    /**
     * 检查材料是否与炼药台中至少一个瓶子反应。
     *
     * <p>使用 {@link BrewingRecipeRegistry#hasMix} 替代原版 {@code PotionBrewing.hasMix}，
     * 支持模组自定义炼药配方。</p>
     */
    private static boolean matchesAnyBottle(BrewingStandBlockEntity brewing, ItemStack ingredient) {
        for (int slot : BOTTLE_SLOTS) {
            ItemStack bottle = brewing.getItem(slot);
            if (!bottle.isEmpty() && BrewingRecipeRegistry.hasMix(bottle, ingredient))
                return true;
        }
        return BrewingRecipeRegistry.isIngredient(ingredient);
    }
}
