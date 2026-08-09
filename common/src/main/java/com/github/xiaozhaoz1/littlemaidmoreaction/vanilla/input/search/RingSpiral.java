package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

/**
 * 环形螺旋 (v77.5 移植自 Numen RingSpiral) — 纯数学, 零 MC 依赖。
 *
 * <p>Ring 0 = 中心单格; ring r &gt; 0 = (2r+1)² 方形外围 8r 格, 逐边枚举:
 * 上边西→东 (缺 NE 角), 右边北→南 (缺 SE), 下边东→西 (缺 SW), 左边南→北 (缺 NW)。
 * 距离序为环近似 (Chebyshev), 精确距离由扫描时计算。
 */
public final class RingSpiral {

    private RingSpiral() {}

    /** 环 r 的格子数: ring 0 = 1, ring r = 8r */
    public static int perimeter(int ring) {
        return ring == 0 ? 1 : 8 * ring;
    }

    /** 环 r 第 idx 格的偏移 (dx, dz); idx ∈ [0, perimeter(r)) */
    public static int[] offset(int ring, int idx) {
        if (ring == 0) return new int[]{0, 0};
        int side = idx / (2 * ring);
        int t = idx % (2 * ring);
        return switch (side) {
            case 0 -> new int[]{-ring + t, -ring};
            case 1 -> new int[]{ring, -ring + t};
            case 2 -> new int[]{ring - t, ring};
            default -> new int[]{-ring, ring - t};
        };
    }
}
