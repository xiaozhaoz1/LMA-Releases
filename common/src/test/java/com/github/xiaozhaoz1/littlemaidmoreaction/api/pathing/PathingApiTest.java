package com.github.xiaozhaoz1.littlemaidmoreaction.api.pathing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PathingApi 纯函数单测 (v79.53) — 到达对齐速度 (NavWatchdog 近程减速语义恢复)。
 * 纯 JVM: 只调 alignVelocity (无 MC 依赖); 不触碰其他成员 (惰性加载)。
 */
class PathingApiTest {

    @Test
    void alignVelocity_格中心_完全停止() {
        // Arrange
        double[] v = PathingApi.alignVelocity(0, 0);
        // Assert
        assertEquals(0, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
    }

    @Test
    void alignVelocity_中心附近_停止() {
        // dist = sqrt(0.02²+0.03²) ≈ 0.036 ≤ 0.05 → 完全停止
        double[] v = PathingApi.alignVelocity(0.02, 0.03);
        assertEquals(0, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
    }

    @Test
    void alignVelocity_减速区_线性衰减() {
        // dist = 0.1 → speed = min(0.2, 0.1*0.5=0.05) = 0.05 (线性)
        double[] v = PathingApi.alignVelocity(0.1, 0);
        assertEquals(0.05, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
    }

    @Test
    void alignVelocity_近程_上限截断() {
        // dist = 0.5 → speed = min(0.2, 0.25) = 0.2 (上限)
        double[] v = PathingApi.alignVelocity(0.5, 0);
        assertEquals(0.2, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
    }

    @Test
    void alignVelocity_方向归一化_指向中心() {
        // (0.5, 0.5) dist ≈ 0.707 → speed = 0.2 → 分量 = 0.2/√2 ≈ 0.1414
        double[] v = PathingApi.alignVelocity(0.5, 0.5);
        double expected = 0.2 / Math.sqrt(2);
        assertEquals(expected, v[0], 1e-9);
        assertEquals(expected, v[1], 1e-9);
        // 方向与 (dx,dz) 同向 (指向格中心)
        assertEquals(0, v[0] * 0.5 - v[1] * 0.5, 1e-9);
    }

    @Test
    void alignVelocity_远端_恒上限速度() {
        // dist = 2.0 → speed = min(0.2, 1.0) = 0.2 (远距离同为减速上限)
        double[] v = PathingApi.alignVelocity(2.0, 0);
        assertEquals(0.2, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
    }
}
