package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import net.minecraft.server.level.ServerLevel;

/**
 * 挖空置域区块强制加载门面 (v79.62.2 瘦身 — 3×3 梯级预载并入 behavior ChunkWorkArea 内置区块缓存).
 *
 * <p>保留 {@link #forceChunk} (传送目标区块 / 输入输出箱区块的按需强制加载, 目标语义, 非工作区缓存)
 * 与 {@link #regionBounds} (起点区块为中心 size 区块的边界纯计算, 供测试与调用方).
 * 工作区 3×3 预载由 {@code LmaFlowCoordinationBehavior} 的 ChunkWorkArea 统一接管.
 */
public final class VoidExcavationChunkManager {

    private VoidExcavationChunkManager() {}

    /** 强制加载/释放区块 — 异步 (setChunkForced 排队 ChunkBuilder, 主线程不阻塞); 调用方必须限数量 */
    public static void forceChunk(ServerLevel world, int cx, int cz, boolean forced) {
        world.setChunkForced(cx, cz, forced);
    }

    /** 区域边界 (纯计算, 可单测): 起点区块为中心 size 区块, 整区块对齐.
     *  <p>v79.62.1 修: 原 maxCX=scx+half-1 在 size=1 时 maxCX<minCX 空区域 → 立即完成;
     *  正确: minCX=scx-size/2, maxCX=minCX+size-1 → 正好 size 个区块.
     *  @return [minCX, maxCX, minCZ, maxCZ, minX, maxX, minZ, maxZ] (8 元素) */
    public static int[] regionBounds(net.minecraft.core.BlockPos start, int size) {
        int scx = start.getX() >> 4, scz = start.getZ() >> 4;
        int minCX = scx - size / 2, maxCX = minCX + size - 1;
        int minCZ = scz - size / 2, maxCZ = minCZ + size - 1;
        return new int[]{minCX, maxCX, minCZ, maxCZ,
                minCX * 16, maxCX * 16 + 15, minCZ * 16, maxCZ * 16 + 15};
    }
}
