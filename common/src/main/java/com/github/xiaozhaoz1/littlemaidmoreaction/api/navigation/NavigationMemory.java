package com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;

/**
 * LMA Brain Memory 导航访问工具 (v49: 移至 api/navigation/)。
 *
 * <p>封装 MemoryModuleType 读写。导航数据不持久化到 NBT，Brain Activity 切换时自动清除。
 *
 * <p><b>依赖方向</b> (2026-09-21 接口倒置): 本类属对外契约层, 不得 import adapter ⇒
 * 记忆类型由实现方经 {@link NavigationMemoryProvider} 在注册期注入
 * (见 {@code adapter.LmaMemoryModuleRegistry.register}); 取值仍按项目纪律放在**调用点** (延迟取值器)。
 */
public final class NavigationMemory {

    /** 实现方注入的供应器 — 注册期写入 (见 {@link #install}); 运行期只读 */
    private static volatile NavigationMemoryProvider provider;

    private NavigationMemory() {}

    /** 由 adapter (TLM 桥接层) 在注册期注入; 幂等, 重复注入同值无副作用 */
    public static void install(NavigationMemoryProvider impl) {
        provider = impl;
    }

    private static NavigationMemoryProvider provider() {
        NavigationMemoryProvider p = provider;
        if (p == null) {
            throw new IllegalStateException("NavigationMemory 未安装供应器 — "
                    + "adapter.LmaMemoryModuleRegistry.register(modBus) 必须在平台入口构造期被调用");
        }
        return p;
    }

    public static BlockPos getNavTarget(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(provider().navTarget().get())
                .orElse(null);
    }

    public static void setNavTarget(EntityMaid maid, BlockPos pos) {
        maid.getBrain().setMemory(provider().navTarget().get(), pos);
    }

    public static void clearNavTarget(EntityMaid maid) {
        maid.getBrain().eraseMemory(provider().navTarget().get());
    }

    public static boolean hasNavTarget(EntityMaid maid) {
        return maid.getBrain().hasMemoryValue(provider().navTarget().get());
    }

    public static long getNavStartTick(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(provider().navStartTick().get())
                .orElse(0L);
    }

    public static void setNavStartTick(EntityMaid maid, long tick) {
        maid.getBrain().setMemory(provider().navStartTick().get(), tick);
    }

    public static void clearNavStartTick(EntityMaid maid) {
        maid.getBrain().eraseMemory(provider().navStartTick().get());
    }

    /** 清除所有 LMA 导航 Memory */
    public static void clearAllNav(EntityMaid maid) {
        clearNavTarget(maid);
        clearNavStartTick(maid);
    }
}
