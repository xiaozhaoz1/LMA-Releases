package littlemaidmoreaction.littlemaidmoreaction.compat.create.task;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 女仆搅拌 IO — Basin 配方检测与执行。
 *
 * <p>层次:
 * <br>Input:   findBasin
 * <br>Compute: hasRecipe
 * <br>Output:  executeMix, playSound
 *
 * <p>匹配 MIXING 配方 (通过 AllRecipeTypes.MIXING)。
 */
public final class MixService {
    private static final int SEARCH_RANGE = 3;
    static final int IDLE_INTERVAL = 60; // 3秒摸鱼

    private MixService() {}

    // ── Input ──

    /** 搜索附近 Basin */
    public static BlockPos findBasin(Level level, BlockPos center) {
        for (int dr = 0; dr <= SEARCH_RANGE; dr++) {
            for (int dx = -dr; dx <= dr; dx++) {
                for (int dz = -dr; dz <= dr; dz++) {
                    if (Math.abs(dx) != dr && Math.abs(dz) != dr) continue;
                    BlockPos pos = center.offset(dx, 0, dz);
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = pos.offset(0, dy, 0);
                        BlockEntity be = level.getBlockEntity(p);
                        if (be instanceof BasinBlockEntity) {
                            return p.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    // ── Compute ──

    /** 检查 Basin 是否有可搅拌配方 */
    public static boolean hasRecipe(Level level, BlockPos pos) {
        if (pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BasinBlockEntity basin)) return false;
        if (!basin.canContinueProcessing()) return false;
        if (basin.isEmpty()) return false;

        var recipes = level.getRecipeManager()
            .getAllRecipesFor(AllRecipeTypes.MIXING.getType());
        for (Recipe<?> r : recipes) {
            if (BasinRecipe.match(basin, r)) {
                return true;
            }
        }
        return false;
    }

    // ── Output ──

    /** 执行搅拌: BasinRecipe.apply() */
    public static boolean executeMix(Level level, BlockPos pos) {
        if (level.isClientSide || pos == null) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BasinBlockEntity basin)) return false;

        var recipes = level.getRecipeManager()
            .getAllRecipesFor(AllRecipeTypes.MIXING.getType());
        for (Recipe<?> r : recipes) {
            if (BasinRecipe.match(basin, r)) {
                BasinRecipe.apply(basin, r);
                basin.notifyChangeOfContents();
                return true;
            }
        }
        return false;
    }

    // ── Sound ──

    /** 播放搅拌工作音效 (每 20 tick，匹配原版 Mixer tickAudio) */
    public static void playMixSound(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        AllSoundEvents.MIXING.playAt(level, pos, 0.75f, 1.0f, true);
    }
}
