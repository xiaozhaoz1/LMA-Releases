package littlemaidmoreaction.littlemaidmoreaction.api.navigation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.adapter.LmaMemoryModuleRegistry;
import net.minecraft.core.BlockPos;

/**
 * LMA Brain Memory 导航访问工具 (v49: 移至 api/navigation/)。
 *
 * <p>封装 MemoryModuleType 读写。导航数据不持久化到 NBT，Brain Activity 切换时自动清除。
 */
public final class NavigationMemory {

    private NavigationMemory() {}

    public static BlockPos getNavTarget(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(LmaMemoryModuleRegistry.NAV_TARGET.get())
                .orElse(null);
    }

    public static void setNavTarget(EntityMaid maid, BlockPos pos) {
        maid.getBrain().setMemory(LmaMemoryModuleRegistry.NAV_TARGET.get(), pos);
    }

    public static void clearNavTarget(EntityMaid maid) {
        maid.getBrain().eraseMemory(LmaMemoryModuleRegistry.NAV_TARGET.get());
    }

    public static boolean hasNavTarget(EntityMaid maid) {
        return maid.getBrain().hasMemoryValue(LmaMemoryModuleRegistry.NAV_TARGET.get());
    }

    public static long getNavStartTick(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(LmaMemoryModuleRegistry.NAV_START_TICK.get())
                .orElse(0L);
    }

    public static void setNavStartTick(EntityMaid maid, long tick) {
        maid.getBrain().setMemory(LmaMemoryModuleRegistry.NAV_START_TICK.get(), tick);
    }

    public static void clearNavStartTick(EntityMaid maid) {
        maid.getBrain().eraseMemory(LmaMemoryModuleRegistry.NAV_START_TICK.get());
    }

    /** 清除所有 LMA 导航 Memory */
    public static void clearAllNav(EntityMaid maid) {
        clearNavTarget(maid);
        clearNavStartTick(maid);
    }
}
