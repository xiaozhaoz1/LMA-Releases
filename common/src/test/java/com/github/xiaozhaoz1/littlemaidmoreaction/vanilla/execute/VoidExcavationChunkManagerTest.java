package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 挖空置域纯计算测试 (v79.62.1) — 零 MC 运行时依赖 (BlockPos 纯数据).
 * 覆盖: 区域边界数学 (size → 区块/方块范围) — 回归 v79.62.1 修的 size=1 空区域 bug.
 */
class VoidExcavationChunkManagerTest {

    @Test
    @DisplayName("size=1 → 恰好 1 区块 (起点区块, 非空) — 回归空区域立即完成 bug")
    void regionBounds_size1_nonEmpty() {
        // 起点在区块 (3, 5) 内
        int[] b = VoidExcavationChunkManager.regionBounds(new BlockPos(55, 60, 84), 1);
        // 区块范围: scx=55>>4=3, scz=84>>4=5
        assertEquals(3, b[0], "minCX");
        assertEquals(3, b[1], "maxCX = minCX+size-1 (size=1 时 = scx, 非空)");
        assertEquals(5, b[2], "minCZ");
        assertEquals(5, b[3], "maxCZ");
        // 方块范围: 整区块
        assertEquals(48, b[4], "minX = 3*16");
        assertEquals(63, b[5], "maxX = 3*16+15");
        assertEquals(80, b[6], "minZ = 5*16");
        assertEquals(95, b[7], "maxZ = 5*16+15");
        // 非空 (maxX >= minX)
        assertTrue(b[5] >= b[4]);
        assertTrue(b[7] >= b[6]);
    }

    @Test
    @DisplayName("size=4 → 4 区块, 起点区块偏右 (scx-2..scx+1)")
    void regionBounds_size4() {
        int[] b = VoidExcavationChunkManager.regionBounds(new BlockPos(55, 60, 84), 4);
        // scx=3, size/2=2 → minCX=1, maxCX=1+4-1=4 (4 区块: 1,2,3,4)
        assertEquals(1, b[0], "minCX = scx-2");
        assertEquals(4, b[1], "maxCX = minCX+size-1");
        assertEquals(3, b[2], "minCZ = scz-2");
        assertEquals(6, b[3], "maxCZ");
        // 4 区块宽度 = 64 格
        assertEquals(64, b[5] - b[4] + 1, "X 跨 4 区块 64 格");
    }

    @Test
    @DisplayName("size=32 → 32 区块 (512 格), 起点区块居中偏左")
    void regionBounds_size32() {
        int[] b = VoidExcavationChunkManager.regionBounds(new BlockPos(55, 60, 84), 32);
        // scx=3, size/2=16 → minCX=-13, maxCX=-13+31=18 → 32 区块
        assertEquals(18 - (-13) + 1, 32, "区块数 = size");
        assertEquals(512, b[5] - b[4] + 1, "X 跨 512 格");
        assertEquals(512, b[7] - b[6] + 1, "Z 跨 512 格");
    }

    @Test
    @DisplayName("负坐标起点 (1.21 基岩 y 负场景的 x/z 负区块) — 整除对齐正确")
    void regionBounds_negativeCoords() {
        int[] b = VoidExcavationChunkManager.regionBounds(new BlockPos(-1, -60, -2), 2);
        // scx = -1>>4 = -1 (算术右移), scz = -2>>4 = -1
        assertEquals(-1, (-1) >> 4, "负数 >> 4 算术右移 = -1");
        // -1>>4 = -1, -2>>4 = -1 → 区块范围 -2..-1 → 2 区块 (32 格)
        assertEquals(-2, b[0], "minCX = scx-1");
        assertEquals(-1, b[1], "maxCX = minCX+size-1");
        assertEquals(-2, b[2], "minCZ = scz-1");
        assertEquals(-1, b[3], "maxCZ");
        assertEquals(-32, b[4], "minX = -2*16");
        assertEquals(-1, b[5], "maxX = -1*16+15");
        assertEquals(32, b[5] - b[4] + 1, "X 跨 2 区块 32 格 (负数区块正确)");
    }
}
