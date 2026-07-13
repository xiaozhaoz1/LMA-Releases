package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.interact;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.recipe.RecipeChain;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.recipe.RecipeIndex;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.input.recipe.RecipeTreeResolver;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * 女仆工作台合成 — 自动配方链合成 (v15)。
 * 参数: item_id(目标产物), 自动合成最大可能数量。
 */
@RuleAction
public class CraftingTableInteractAction extends AbstractFunctionalBlockInteraction {

    @Override public String id() { return "craft_chain"; }
    @Override public String displayName() { return "工作台合成"; }
    @Override protected String defaultBlockId() { return "minecraft:crafting_table"; }
    @Override protected List<String> validActions() { return List.of(); }

    @Override
    public List<TypedParam<?>> params() {
        List<TypedParam<?>> all = new ArrayList<>();
        all.addAll(super.params()); // block_id, range, vertical, max, item_id
        return List.copyOf(all);
    }

    @Override
    protected void doInteract(BlockPos pos, BlockState state, EntityMaid maid,
                               Map<String, String> params, String action) {
        doCraftChain(maid, params);
    }

    private void doCraftChain(EntityMaid maid, Map<String, String> params) {
        String itemId = params.getOrDefault("item_id", "");
        if (itemId.isEmpty()) return;

        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) return;
        Item targetItem = ForgeRegistries.ITEMS.getValue(rl);
        if (targetItem == null) return;

        IItemHandler maidInv = getMaidInventory(maid);
        Map<Item, Integer> available = new HashMap<>();
        for (int i = 0; i < maidInv.getSlots(); i++) {
            ItemStack s = maidInv.getStackInSlot(i);
            if (!s.isEmpty()) available.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        // ★ 排除目标产物: 已有木板不影响继续用原木做木板
        available.remove(targetItem);

        RecipeIndex idx = RecipeIndex.get(maid.level());
        RecipeChain probe = RecipeTreeResolver.resolve(
            targetItem, 1, available, idx, RecipeTreeResolver.DEFAULT_MAX_DEPTH, maid.level().registryAccess());
        if (probe == null || probe.cost().isEmpty()) return;

        int maxBatches = Integer.MAX_VALUE;
        for (var e : probe.cost().entrySet()) {
            int pb = e.getValue();
            if (pb <= 0) continue;
            maxBatches = Math.min(maxBatches, available.getOrDefault(e.getKey(), 0) / pb);
        }
        if (maxBatches <= 0) return;

        RecipeChain chain = RecipeTreeResolver.resolve(
            targetItem, maxBatches, available, idx, RecipeTreeResolver.DEFAULT_MAX_DEPTH, maid.level().registryAccess());
        if (chain == null || chain.steps().isEmpty()) return;

        Map<Item, Integer> buffer = new HashMap<>(available);
        for (var step : chain.steps()) {
            Map<Item, Integer> needed = new HashMap<>();
            for (Ingredient ing : step.recipe().getIngredients()) {
                if (ing.isEmpty()) continue;
                ItemStack[] matches = ing.getItems();
                if (matches.length == 0) continue;
                needed.merge(matches[0].getItem(), step.craftCount() * matches[0].getCount(), Integer::sum);
            }
            for (var entry : needed.entrySet()) {
                if (buffer.getOrDefault(entry.getKey(), 0) < entry.getValue()) return;
            }
            if (step.dependsOn().isEmpty()) {
                for (var entry : needed.entrySet()) {
                    consumeFromMaidInv(maidInv, entry.getKey(), entry.getValue());
                    buffer.merge(entry.getKey(), -entry.getValue(), Integer::sum);
                }
            } else {
                for (var entry : needed.entrySet())
                    buffer.merge(entry.getKey(), -entry.getValue(), Integer::sum);
            }
            ItemStack result = step.recipe().getResultItem(maid.level().registryAccess());
            buffer.merge(step.output(), result.getCount() * step.craftCount(), Integer::sum);
        }

        spawnPickup(maid, new ItemStack(targetItem, maxBatches));
        playInteractionFeedback(maid, BlockPos.ZERO, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT);
        completeFlowTask(maid);
        LittleMaidMoreAction.LOGGER.info("[CraftChain] {}x{} ", maxBatches, itemId);
    }

    private static boolean consumeFromMaidInv(IItemHandler inv, Item item, int amount) {
        int r = amount;
        for (int i = 0; i < inv.getSlots() && r > 0; i++) {
            if (inv.getStackInSlot(i).is(item))
                r -= inv.extractItem(i, Math.min(r, inv.getStackInSlot(i).getCount()), false).getCount();
        }
        return r == 0;
    }

    private static void spawnPickup(EntityMaid maid, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity e = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.5, maid.getZ(), stack);
        e.setPickUpDelay(0);
        maid.level().addFreshEntity(e);
    }
}
