package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link UnstuckDetector} 纯 JVM 测试 (v79.61x) — 卡住签名检测
 * (滚动窗口 / 尝试占比 / 空闲不误报 — Numen 原语义逐项验证)。
 */
class UnstuckDetectorTest {

    @Test
    @DisplayName("窗口未满不报 — 样本不足 40t 恒 false")
    void window_not_full() {
        UnstuckDetector d = new UnstuckDetector();
        for (int i = 0; i < 39; i++) {
            d.record(0, 0, true);
        }
        assertFalse(d.isStuck(), "39t < 40t 窗口不报");
        assertEquals(39, d.size());
    }

    @Test
    @DisplayName("卡住签名: 全窗口尝试移动但位置不动 → true")
    void stuck_signature() {
        UnstuckDetector d = new UnstuckDetector();
        for (int i = 0; i < 40; i++) {
            d.record(5.0, 5.0, true);
        }
        assertTrue(d.isStuck(), "40t 全尝试未动 = 卡住");
    }

    @Test
    @DisplayName("空闲不误报: 无移动意图的 tick 不计入尝试")
    void idle_never_stuck() {
        UnstuckDetector d = new UnstuckDetector();
        for (int i = 0; i < 40; i++) {
            d.record(5.0, 5.0, false);
        }
        assertFalse(d.isStuck(), "全程无导航意图 = 正常待机");
    }

    @Test
    @DisplayName("混合窗口: 尝试占比 < 80% 不报")
    void low_trying_fraction() {
        UnstuckDetector d = new UnstuckDetector();
        // 40t 中 30t 尝试 (75%) + 10t 空闲 — 不足 ceil(40*0.8)=32
        for (int i = 0; i < 40; i++) {
            d.record(0, 0, i < 30);
        }
        assertFalse(d.isStuck(), "75% < 80% 不报");
    }

    @Test
    @DisplayName("混合窗口: 尝试占比 ≥80% 且未动 → true")
    void enough_trying_fraction() {
        UnstuckDetector d = new UnstuckDetector();
        for (int i = 0; i < 40; i++) {
            d.record(0, 0, i < 32);   // 80% 尝试
        }
        assertTrue(d.isStuck());
    }

    @Test
    @DisplayName("移动超阈值不报: 位置漂出 0.75 格圆盘")
    void moved_not_stuck() {
        UnstuckDetector d = new UnstuckDetector();
        for (int i = 0; i < 40; i++) {
            d.record(i * 0.05, 0, true);   // 累计移动 2 格
        }
        assertFalse(d.isStuck(), "2 格位移 > 0.75 圆盘 = 在动");
    }

    @Test
    @DisplayName("圆盘内小幅移动仍报: 位移 < 0.75 格")
    void tiny_move_still_stuck() {
        UnstuckDetector d = new UnstuckDetector();
        for (int i = 0; i < 40; i++) {
            d.record(i * 0.01, 0, true);   // 累计 0.4 格 — 圆盘内
        }
        assertTrue(d.isStuck(), "0.4 格 < 0.75 圆盘 = 仍卡");
    }

    @Test
    @DisplayName("reset 清窗: 清空后窗口未满不报")
    void reset_clears_window() {
        UnstuckDetector d = new UnstuckDetector();
        for (int i = 0; i < 40; i++) {
            d.record(0, 0, true);
        }
        assertTrue(d.isStuck());
        d.reset();
        assertEquals(0, d.size());
        assertFalse(d.isStuck());
    }

    @Test
    @DisplayName("窗口滚动: 走出卡住后新窗口重新评估")
    void rolling_window_recovery() {
        UnstuckDetector d = new UnstuckDetector();
        for (int i = 0; i < 40; i++) {
            d.record(0, 0, true);
        }
        assertTrue(d.isStuck());
        // 脱困: 接下来 40t 持续移动 — 滚动窗口逐渐被新样本替换
        for (int i = 0; i < 40; i++) {
            d.record(i * 0.1, 0, true);
        }
        assertFalse(d.isStuck(), "脱困移动 40t 后窗口全是新样本");
    }
}
