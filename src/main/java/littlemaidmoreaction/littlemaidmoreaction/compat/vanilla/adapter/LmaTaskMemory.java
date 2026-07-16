package littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.adapter;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;

/**
 * LMA Brain Memory 工具 (v12.7 P0)。
 *
 * <p>封装 MemoryModuleType 读写 — 替代 raw PersistentData。
 * 导航数据不再持久化到 NBT，Brain Activity 切换时自动清除。
 */
public final class LmaTaskMemory {

    private LmaTaskMemory() {}

    // ── 导航目标 ──

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

    // ── 导航启动时间 ──

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

    // ── 清理 ──

    /** 清除所有 LMA 导航 Memory */
    public static void clearAllNav(EntityMaid maid) {
        clearNavTarget(maid);
        clearNavStartTick(maid);
    }
}
