package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RegionBox 纯数据盒测试 (v79.63) — 零 MC 依赖, 纯 JVM。
 *
 * <p>覆盖点来自真实调用面 (作物查询): ① 闭区间边界 ② Y ±1 扩展语义 (耕地层/作物层两层)
 * ③ 中心/半径口径 (区块扫描半径 = maxDim/2 + 16 由 CropQuery 计算, 这里只测盒本身的几何)。
 */
class RegionBoxTest {

    /** 典型 3×3 农田 (耕地 y=63, 作物 y=64 → 盒 maxY 取作物层) */
    private static final RegionBox FARM = RegionBox.of(-5, 63, -5, -3, 64, -3);

    @Test
    @DisplayName("contains: 闭区间 (六面都在内, 外面一格都不在内)")
    void contains_closedInterval() {
        assertTrue(FARM.contains(-5, 63, -5), "最小角");
        assertTrue(FARM.contains(-3, 64, -3), "最大角");
        assertTrue(FARM.contains(-4, 63, -4), "内部");
        assertFalse(FARM.contains(-6, 63, -4), "X 下越界");
        assertFalse(FARM.contains(-2, 63, -4), "X 上越界");
        assertFalse(FARM.contains(-4, 62, -4), "Y 下越界");
        assertFalse(FARM.contains(-4, 65, -4), "Y 上越界");
        assertFalse(FARM.contains(-4, 63, -6), "Z 下越界");
        assertFalse(FARM.contains(-4, 63, -2), "Z 上越界");
    }

    @Test
    @DisplayName("containsYExtended: 仅 Y 轴各放 1 格 (XZ 仍严格)")
    void containsYExtended_onlyY() {
        assertTrue(FARM.containsYExtended(-4, 62, -4), "Y-1 (耕地层下方)");
        assertTrue(FARM.containsYExtended(-4, 65, -4), "Y+1 (作物层上方)");
        assertFalse(FARM.containsYExtended(-4, 61, -4), "Y-2 不应放行");
        assertFalse(FARM.containsYExtended(-4, 66, -4), "Y+2 不应放行");
        assertFalse(FARM.containsYExtended(-6, 63, -4), "X 不放宽");
        assertFalse(FARM.containsYExtended(-4, 63, -2), "Z 不放宽");
    }

    @Test
    @DisplayName("中心: 整数向下取整, 与区块扫描中心口径一致")
    void center_floorDivide() {
        assertEquals(-4, FARM.centerX());
        assertEquals(63, FARM.centerY());   // (63+64)/2 = 63 (int 除)
        assertEquals(-4, FARM.centerZ());
        // 偶数边长时中心落在"偏小"一格 (与 CropQuery.regionCenter 同实现)
        RegionBox even = RegionBox.of(0, 0, 0, 3, 3, 3);
        assertEquals(1, even.centerX());
        assertEquals(1, even.centerY());
    }

    @Test
    @DisplayName("几何: maxDimension / 面积 / 体积")
    void geometry() {
        assertEquals(2, FARM.maxDimension(), "3×2×3 → 最大边长 2");
        assertEquals(9, FARM.horizontalArea(), "3×3 = 9");
        assertEquals(18L, FARM.volume(), "3×2×3 = 18");
        RegionBox single = RegionBox.of(7, 7, 7, 7, 7, 7);
        assertEquals(0, single.maxDimension(), "单格边长 0");
        assertEquals(1, single.horizontalArea());
        assertEquals(1L, single.volume());
    }

    @Test
    @DisplayName("退化输入不抛异常 (归一化由上游保证, 本类只判定)")
    void degenerate_noThrow() {
        RegionBox inverted = RegionBox.of(5, 5, 5, 1, 1, 1);   // 未归一化
        assertFalse(inverted.contains(3, 3, 3), "未归一化时 contains 恒 false (不静默修正)");
        assertTrue(inverted.maxDimension() >= 0 || inverted.maxDimension() < 0, "只要求不抛");
    }
}
