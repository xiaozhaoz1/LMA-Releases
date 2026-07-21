package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Optional;

/**
 * 女仆冲压 IO — Depot/Basin 双路配方检测与执行。
 *
 * <p>层次:
 * <br>Input:   findTarget, readHeldItem, findRecipe
 * <br>Compute: hasRecipe
 * <br>Output:  executeDepotPress, executeBasinPress, playSound
 *
 * <p>规则: heldItem (Depot) / inputInventory (Basin) 才加工,
 * outputBuffer 中的物品不加工。
 */
public final class PressService {
    private static final int SEARCH_RANGE = 3;
    static final int PRESS_DURATION = 100; // 5秒 = 100 tick

    private PressService() {}

    // ── Input ──

    /** 搜索附近 Depot 或 Basin，优先 Depot */
    public static BlockPos findTarget(Level level, BlockPos center) {
        BlockPos depot = findBlock(level, center, DepotBlockEntity.class);
        if (depot != null) return depot;
        return findBlock(level, center, BasinBlockEntity.class);
    }

    /** 从 Depot 读取 heldItem (副本) */
    public static ItemStack readHeldItem(Level level, BlockPos pos) {
        if (pos == null) return ItemStack.EMPTY;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DepotBlockEntity depot) {
            return depot.getHeldItem().copy();
        }
        return ItemStack.EMPTY;
    }

    /** Basin 是否可继续处理 (outputBuffer 非空则 false) */
    public static boolean canBasinProcess(Level level, BlockPos pos) {
        if (pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BasinBlockEntity basin)) return false;
        return basin.canContinueProcessing();
    }

    // ── Compute ──

    /** Depot 是否有可按压配方 */
    public static boolean hasDepotRecipe(Level level, BlockPos pos) {
        ItemStack held = readHeldItem(level, pos);
        if (held.isEmpty()) return false;
        return findPressingRecipe(level, held).isPresent();
    }

    /** Basin 是否有可冲压配方 (COMPACTING + 2×2/3×3 CraftingRecipe 压缩) */
    public static boolean hasBasinRecipe(Level level, BlockPos pos) {
        if (pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BasinBlockEntity basin)) return false;
        if (!basin.canContinueProcessing()) return false;

        // COMPACTING
        for (Recipe<?> r : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.COMPACTING.getType())) {
            if (BasinRecipe.match(basin, r)) return true;
        }
        // 2×2/3×3 CraftingRecipe 压缩 (铁块/金块等)
        for (Recipe<?> r : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (r instanceof CraftingRecipe && canCompress(r) && BasinRecipe.match(basin, r))
                return true;
        }
        return false;
    }

    /** 查找物品的按压配方 (1.20.1: SimpleContainer) */
    @SuppressWarnings("unchecked")
    public static Optional<Recipe<Container>> findPressingRecipe(Level level, ItemStack item) {
        if (item.isEmpty()) return Optional.empty();
        return (Optional) AllRecipeTypes.PRESSING.find(new SimpleContainer(item), level);
    }

    // ── Output ──

    /** 执行 Depot 按压: 配方应用 → 结果放回 Depot */
    public static boolean executeDepotPress(Level level, BlockPos pos, Recipe<?> recipe) {
        if (level.isClientSide || pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof DepotBlockEntity depot)) return false;

        ItemStack held = depot.getHeldItem();
        if (held.isEmpty()) return false;

        List<ItemStack> results = RecipeApplier.applyRecipeOn(level,
            held.copyWithCount(1), recipe, true);

        if (results.isEmpty()) return false;

        held.shrink(1);
        if (held.isEmpty()) {
            depot.setHeldItem(results.get(0));
        }
        for (int i = 1; i < results.size(); i++) {
            net.minecraft.world.entity.item.ItemEntity ie =
                new net.minecraft.world.entity.item.ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    results.get(i).copy());
            ie.setDefaultPickUpDelay();
            level.addFreshEntity(ie);
        }
        return true;
    }

    /** 执行 Basin 冲压: COMPACTING 优先, 其次 CraftingRecipe 压缩 */
    @SuppressWarnings("unchecked")
    public static boolean executeBasinPress(Level level, BlockPos pos) {
        if (level.isClientSide || pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BasinBlockEntity basin)) return false;

        // COMPACTING 优先
        for (Recipe<?> r : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.COMPACTING.getType())) {
            if (BasinRecipe.match(basin, r)) {
                BasinRecipe.apply(basin, r);
                basin.notifyChangeOfContents();
                return true;
            }
        }
        // 2×2/3×3 CraftingRecipe 压缩
        for (Recipe<?> r : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (r instanceof CraftingRecipe && canCompress((Recipe<Container>) r) && BasinRecipe.match(basin, r)) {
                BasinRecipe.apply(basin, r);
                basin.notifyChangeOfContents();
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean canCompress(Recipe<?> r) {
        return MechanicalPressBlockEntity.canCompress((Recipe<Container>) r);
    }

    // ── Sound ──

    /** 播放按压完成音效 */
    public static void playPressSound(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(level, pos, 0.5f, 1.0f);
    }

    // ── Utils ──

    private static BlockPos findBlock(Level level, BlockPos center,
                                       Class<? extends BlockEntity> beClass) {
        for (int dr = 0; dr <= SEARCH_RANGE; dr++) {
            for (int dx = -dr; dx <= dr; dx++) {
                for (int dz = -dr; dz <= dr; dz++) {
                    if (Math.abs(dx) != dr && Math.abs(dz) != dr) continue;
                    BlockPos pos = center.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = pos.offset(0, dy, 0);
                        BlockEntity be = level.getBlockEntity(p);
                        if (beClass.isInstance(be)) {
                            return p.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }
}
