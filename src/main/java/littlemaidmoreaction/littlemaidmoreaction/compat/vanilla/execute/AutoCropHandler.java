package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute;

import com.github.tartaricacid.touhoulittlemaid.api.task.ISpecialCropHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import littlemaidmoreaction.littlemaidmoreaction.event.MaidHarvestCropEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动作物匹配处理器 — 替代旧白名单。
 *
 * <p>harvest记录作物 → canPlant匹配种子 → 拒绝不匹配。
 * <p>未启用时走 TLM 原版逻辑。
 */
public final class AutoCropHandler implements ISpecialCropHandler {

    static volatile boolean GLOBAL_ENABLED = false;
    static final Set<UUID> ENABLED_MAIDS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Block> LAST_CROP = new ConcurrentHashMap<>();

    @Override
    public void harvest(EntityMaid maid, BlockPos cropPos, BlockState cropState, boolean isDestroyMode) {
        Block cropBlock = cropState.getBlock();
        MinecraftForge.EVENT_BUS.post(new MaidHarvestCropEvent(maid, cropPos, cropBlock));

        if (!isEnabled(maid)) {
            ISpecialCropHandler.super.harvest(maid, cropPos, cropState, isDestroyMode);
            return;
        }

        LAST_CROP.put(maid.getUUID(), cropBlock);
        ISpecialCropHandler.super.harvest(maid, cropPos, cropState, isDestroyMode);

        if (!isDestroyMode) {
            maid.level().setBlock(cropPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean canPlant(EntityMaid maid, BlockPos basePos, BlockState baseState, ItemStack seed) {
        if (!isEnabled(maid)) {
            return ISpecialCropHandler.super.canPlant(maid, basePos, baseState, seed);
        }

        Block expectedCrop = LAST_CROP.remove(maid.getUUID());
        if (expectedCrop != null) {
            if (seed.getItem() instanceof ItemNameBlockItem seedItem
                    && seedItem.getBlock() == expectedCrop) {
                return ISpecialCropHandler.super.canPlant(maid, basePos, baseState, seed);
            }
            return false;
        }

        return ISpecialCropHandler.super.canPlant(maid, basePos, baseState, seed);
    }

    public static boolean isEnabled(EntityMaid maid) {
        return ENABLED_MAIDS.contains(maid.getUUID()) || GLOBAL_ENABLED;
    }

    public static void setMaid(EntityMaid maid, boolean enabled) {
        if (enabled) { ENABLED_MAIDS.add(maid.getUUID()); }
        else { ENABLED_MAIDS.remove(maid.getUUID()); }
    }

    public static void setGlobal(boolean enabled) { GLOBAL_ENABLED = enabled; }

    public static void onMaidUnload(UUID uuid) {
        ENABLED_MAIDS.remove(uuid);
        LAST_CROP.remove(uuid);
    }
}
