package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MlgRescueCoordinator} 纯判据 JVM 测试 (v79.61x) —
 * 摔落触发三条件 + 沉稳判定 (Numen 原语义逐项验证)。
 */
class MlgJudgeTest {

    @Test
    @DisplayName("grounded 任一安全态 → 不触发 (脚落地/水中/游泳/爬梯藤)")
    void grounded_suppresses() {
        assertFalse(MlgRescueCoordinator.mlgTriggered(true, -1.0, true), "落地不触发");
    }

    @Test
    @DisplayName("无救命物 → 不触发 (抢了身体也救不了)")
    void no_save_item() {
        assertFalse(MlgRescueCoordinator.mlgTriggered(false, -1.0, false), "无水桶软方块不触发");
    }

    @Test
    @DisplayName("速度阈值: fallSpeed ≤ -0.7 触发")
    void threshold_edge() {
        assertTrue(MlgRescueCoordinator.mlgTriggered(false, -0.7, true), "-0.7 边界触发");
        assertTrue(MlgRescueCoordinator.mlgTriggered(false, -0.9, true), "更快必触发");
    }

    @Test
    @DisplayName("速度阈值: fallSpeed > -0.7 不触发 (普通下落/走台阶)")
    void above_threshold() {
        assertFalse(MlgRescueCoordinator.mlgTriggered(false, -0.6, true), "慢速不触发");
        assertFalse(MlgRescueCoordinator.mlgTriggered(false, 0.0, true), "静止不触发");
    }

    @Test
    @DisplayName("沉稳判定: 速度 ≥ -0.5 = 已落水沉稳 (该收桶)")
    void settled_edge() {
        assertTrue(MlgRescueCoordinator.settled(-0.5), "-0.5 边界沉稳");
        assertTrue(MlgRescueCoordinator.settled(0.0), "速度归零沉稳");
        assertFalse(MlgRescueCoordinator.settled(-0.7), "自由落体中不收桶");
    }
}
