package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ShellOffsets} 纯 JVM 单测（错题 #174：纯逻辑抽纯类 ⇒ 可纯 JVM 测 ✓）。
 *
 * <p>背景（v79.64 排查）：采矿远扫日志出现
 * {@code [ORE-SCAN] 空结果 r=16 访问=16385 filter通过=0 开口拒=0 预算用尽=true} ✗
 * —— 12 格外的矿（d²=144，本应在第 ~4300 次访问时遇到）**一次都没进 filter** ⇒ 怀疑
 * <b>偏移表漏格</b> ✗。本测试把"表到底对不对"钉死 ✓。
 */
class ShellOffsetsTest {

    /** 表里必须有 (12,0,0) —— 且它的桶号正是 d² = 144 ✓ */
    @Test
    void bucketAt144ContainsTwelveZeroZero() {
        ShellOffsets off = ShellOffsets.of(16, 16);
        int[] arr = off.bucket(144);
        assertNotNull(arr, "d²=144 的桶不该是 null（16 格球内必有该距离的格）");
        boolean found = false;
        for (int i = 0; i < arr.length; i += 3) {
            if (arr[i] == 12 && arr[i + 1] == 0 && arr[i + 2] == 0) {
                found = true;
                break;
            }
        }
        assertTrue(found, "d²=144 的桶里必须包含偏移 (12,0,0)");
    }

    /** 中心格必须在桶 0 ✓ */
    @Test
    void bucketZeroIsCenter() {
        ShellOffsets off = ShellOffsets.of(4, 4);
        int[] arr = off.bucket(0);
        assertNotNull(arr);
        assertEquals(3, arr.length, "桶 0 只该有中心一格 (3 个 int)");
        assertEquals(0, arr[0]);
        assertEquals(0, arr[1]);
        assertEquals(0, arr[2]);
    }

    /**
     * **球内格数**必须与暴力枚举一致 ✓（这是"漏格"最直接的判别）
     * 4 格球 = 257 ✓ · 10 格球 = 4169 ✓ · 16 格球 = 17077 ✓（都由本测试现算，不写死魔数）
     */
    @Test
    void sphereCellCountsMatchBruteForce() {
        for (int r : new int[]{4, 10, 16}) {
            ShellOffsets off = ShellOffsets.of(r, r);
            int viaTable = 0;
            for (int k = 0; k <= r * r; k++) viaTable += off.cellCount(k);
            int brute = 0;
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -r; dy <= r; dy++)
                    for (int dz = -r; dz <= r; dz++)
                        if (dx * dx + dy * dy + dz * dz <= r * r) brute++;
            assertEquals(brute, viaTable, "r=" + r + " 时 表内球格数 必须等于暴力枚举");
        }
    }

    /**
     * **迭代顺序**：逐桶 `k=0,1,2,…` 访问时，**每个格子的 d² 单调不减** ✓（这就是"由近到远"的保证 ✓）
     * —— 同时把"访问第 N 格时的最大 d²"报出来，便于对照 `[ORE-SCAN]` 日志 ✓
     */
    @Test
    void iterationIsMonotonicByDistance() {
        ShellOffsets off = ShellOffsets.of(16, 16);
        int lastK = -1;
        long visited = 0;
        for (int k = 0; k <= 16 * 16; k++) {
            int[] arr = off.bucket(k);
            if (arr == null) continue;
            for (int i = 0; i < arr.length; i += 3) {
                int d2 = arr[i] * arr[i] + arr[i + 1] * arr[i + 1] + arr[i + 2] * arr[i + 2];
                assertEquals(k, d2, "桶号必须等于偏移的真实 d²");
                assertTrue(d2 >= lastK, "遍历顺序必须按 d² 单调不减");
                lastK = d2;
                visited++;
                if (visited == 4300) {
                    // 12 格 (d²=144) 必须**早就**被访问到 ⇒ 用于排除"漏格"✗
                    assertTrue(d2 <= 144,
                            "访问到第 4300 格时 d² 应 ≤144（12 格外矿早就该遇到）— 实际 d²=" + d2);
                }
            }
        }
    }
}
