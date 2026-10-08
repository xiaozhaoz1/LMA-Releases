package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LookAroundGrid 纯逻辑测试 (v77.6) — ColumnFacts 测试桩 (零 MC 依赖) 驱动 classify/渲染。
 */
class LookAroundGridTest {

    /** 测试桩: standMap 记录可站位置, lava/liquid/tree 按坐标标记 */
    private static final class StubFacts implements LookAroundGrid.ColumnFacts {
        final Set<Long> stand = new HashSet<>();
        final Set<Long> lava = new HashSet<>();
        final Set<Long> liquid = new HashSet<>();
        final Set<Long> tree = new HashSet<>();

        StubFacts(int feetY, int centerX, int centerZ) {
            // 默认: 中心周围平地 (feetY 可站)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    stand.add(key(centerX + dx, feetY, centerZ + dz));
                }
            }
        }

        private static long key(int x, int y, int z) {
            return ((long) x << 32) | ((long) y << 16) | (z & 0xFFFF);
        }

        @Override public boolean isLavaAt(int x, int y, int z) { return lava.contains(key(x, y, z)); }
        @Override public boolean isLiquidAt(int x, int y, int z) { return liquid.contains(key(x, y, z)); }
        @Override public boolean isTreeAt(int x, int y, int z) { return tree.contains(key(x, y, z)); }
        @Override public boolean canStandAt(int x, int y, int z) { return stand.contains(key(x, y, z)); }
    }

    @Test
    @DisplayName("平地 classify → FLAT")
    void classify_flat() {
        StubFacts f = new StubFacts(10, 0, 0);
        assertEquals(LookAroundGrid.FLAT, LookAroundGrid.classify(f, 1, 10, 0));
    }

    @Test
    @DisplayName("上 1 阶 → STEP_UP; 下 1-2 → STEP_DOWN; 掉 3+ → DROP")
    void classify_verticalAffordance() {
        StubFacts f = new StubFacts(10, 0, 0);
        // 上 1: (2,0) 在 y=11 可站
        f.stand.add(StubFacts.key(2, 11, 0));
        assertEquals(LookAroundGrid.STEP_UP, LookAroundGrid.classify(f, 2, 10, 0));
        // 下 1: (3,0) 在 y=9 可站
        f.stand.add(StubFacts.key(3, 9, 0));
        assertEquals(LookAroundGrid.STEP_DOWN, LookAroundGrid.classify(f, 3, 10, 0));
        // 掉 3: (4,0) 在 y=7 可站 (feetY-3 = DROP)
        f.stand.add(StubFacts.key(4, 7, 0));
        assertEquals(LookAroundGrid.DROP, LookAroundGrid.classify(f, 4, 10, 0));
    }

    @Test
    @DisplayName("岩浆 → HAZARD; 液体 → WATER")
    void classify_hazards() {
        StubFacts f = new StubFacts(10, 0, 0);
        f.lava.add(StubFacts.key(5, 10, 0));
        assertEquals(LookAroundGrid.HAZARD, LookAroundGrid.classify(f, 5, 10, 0));
        f.liquid.add(StubFacts.key(6, 10, 0));
        assertEquals(LookAroundGrid.WATER, LookAroundGrid.classify(f, 6, 10, 0));
    }

    @Test
    @DisplayName("无站立面 → 树 TREE / 墙 WALL")
    void classify_unstandable() {
        StubFacts f = new StubFacts(10, 0, 0);
        f.tree.add(StubFacts.key(7, 10, 0));
        assertEquals(LookAroundGrid.TREE, LookAroundGrid.classify(f, 7, 10, 0));
        assertEquals(LookAroundGrid.WALL, LookAroundGrid.classify(f, 8, 10, 0));
    }

    @Test
    @DisplayName("渲染: 中心 @ + 危险膨胀 x")
    void render_centerAndInflation() {
        StubFacts f = new StubFacts(10, 0, 0);
        f.lava.add(StubFacts.key(1, 10, 0));   // 中心东 1 格岩浆
        String out = LookAroundGrid.render(f, new BlockPos(0, 10, 0), 2);
        // 中心行 (r=2, dz=0): 西→东 = ...@!.
        String[] lines = out.split("\n");
        String centerRow = lines[4];   // 2 行说明 + 1 空行 + 5 行网格
        assertTrue(centerRow.contains("@"), "中心应有 @");
        assertTrue(centerRow.contains("!"), "岩浆格应有 !");
        // 岩浆旁的平地 (r=2,c=4 的东邻) 膨胀为 x — 检查整图含 x
        assertTrue(out.contains("x"), "危险旁应膨胀为 x: \n" + out);
    }
}
