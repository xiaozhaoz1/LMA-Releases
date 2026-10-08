package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RingSpiral 纯 JVM 测试 (v77.5) — 环形螺旋数学: 周长/偏移唯一性/边界枚举。
 */
class RingSpiralTest {

    @Test
    @DisplayName("环周长: ring 0 = 1, ring r = 8r")
    void perimeter_correct() {
        assertEquals(1, RingSpiral.perimeter(0));
        assertEquals(8, RingSpiral.perimeter(1));
        assertEquals(16, RingSpiral.perimeter(2));
        assertEquals(40, RingSpiral.perimeter(5));
    }

    @Test
    @DisplayName("ring 0 偏移 = 中心 (0,0)")
    void offset_ring0_center() {
        assertArrayEquals(new int[]{0, 0}, RingSpiral.offset(0, 0));
    }

    @Test
    @DisplayName("环 r 偏移唯一且 Chebyshev 距离 = r (方形外围)")
    void offset_ring_r_uniqueAndChebyshev() {
        for (int r : new int[]{1, 2, 3, 5}) {
            Set<Long> seen = new HashSet<>();
            for (int idx = 0; idx < RingSpiral.perimeter(r); idx++) {
                int[] off = RingSpiral.offset(r, idx);
                long key = ((long) off[0] << 32) | (off[1] & 0xFFFFFFFFL);
                assertTrue(seen.add(key), "环 " + r + " idx=" + idx + " 偏移重复: " + off[0] + "," + off[1]);
                assertEquals(r, Math.max(Math.abs(off[0]), Math.abs(off[1])),
                        "环 " + r + " idx=" + idx + " 非方形外围");
            }
            // 总数 = 环内全格 (含内圈)
            int total = 0;
            for (int i = 0; i <= r; i++) total += RingSpiral.perimeter(i);
            assertEquals((2 * r + 1) * (2 * r + 1), total, "环 0..r 全覆盖 (2r+1)²");
        }
    }

}
