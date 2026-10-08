package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.maid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一气泡**分级冷却**的纯函数语义 (用户裁定 2026-09-14):
 * 第 1 次后 5 秒 (100t) → 第 2 次后 10 秒 (200t) → 第 3 次起 30 秒 (600t) 循环。
 *
 * <p>本类只测"次数 → 间隔"映射与升级节奏 (冷却中被挡时**不推进**计数 ✓);
 * 真实 tick 驱动下的端到端行为由 MaidChatBubbleApi 的调用点保证 (那里读 gameTime)。
 */
class BubbleThrottleTest {

    @Test
    void intervalEscalatesAndCaps() {
        assertEquals(100L, ThrottleMath.bubbleInterval(1), "第 1 次后应 5 秒");
        assertEquals(200L, ThrottleMath.bubbleInterval(2), "第 2 次后应 10 秒");
        assertEquals(600L, ThrottleMath.bubbleInterval(3), "第 3 次后应 30 秒");
        assertEquals(600L, ThrottleMath.bubbleInterval(9), "第 3 次起应保持 30 秒循环");
        assertEquals(100L, ThrottleMath.bubbleInterval(0), "非法次数按第 1 次处理");
        assertEquals(100L, ThrottleMath.bubbleInterval(-5), "负值按第 1 次处理");
    }

    /** 模拟真实节奏: 同一气泡显示后, 必须等满对应间隔才能再显示; 被挡时不推进档位 */
    @Test
    void escalationRhythm() {
        // ⚠ 边界: ThrottleMath 用 last==0 表示"未标记"(不冷却) ⇒ 模拟从非 0 时刻起,
        //   否则会被哨兵语义误导 (真实世界里 gameTime 从 0 附近开始, 首 tick 属同一情形 ✓)
        final long t0 = 1000;
        long last = t0;          // t0 刚显示第 1 次
        int repeat = 1;
        int shownCount = 1;
        for (long t = t0 + 1; t <= t0 + 1500; t++) {
            boolean allowed = !ThrottleMath.isCoolingDown(t, last, ThrottleMath.bubbleInterval(repeat));
            if (!allowed) {
                continue;        // 冷却中: 不显示也不推进 count ✓
            }
            last = t;
            repeat = Math.min(3, repeat + 1);
            shownCount++;
        }
        // t0 第1次 → +100 第2次 → +300 第3次 → 之后每 600: +900, +1500 ⇒ 共 5 次
        assertEquals(5, shownCount, "1500t 内应恰好显示 5 次 (t0/t0+100/t0+300/t0+900/t0+1500)");
        assertEquals(3, repeat, "档位应封顶到 3 (之后 30 秒循环)");
        // 循环结束时 last = t0+1500 (最后一次显示); 30 秒档下 last+599 仍冷却, last+600 放行 ✓
        assertTrue(ThrottleMath.isCoolingDown(t0 + 1500 + 599, last, ThrottleMath.bubbleInterval(3)),
                "第 3 次之后应处于 30 秒冷却中 (599t 仍未到)");
        assertFalse(ThrottleMath.isCoolingDown(t0 + 1500 + 600, last, ThrottleMath.bubbleInterval(3)),
                "满 600t 应放行 (30 秒循环 ✓)");
        // 哨兵边界: last==0 视为未标记 ⇒ 不冷却 (首次显示前 ✓)
        assertFalse(ThrottleMath.isCoolingDown(50, 0, 100), "last==0 是未标记哨兵, 不应判冷却");
    }
}
