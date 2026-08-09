package com.github.xiaozhaoz1.littlemaidmoreaction.chatbubble;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MaidChatBubbleApi#shouldThrottle} 节流纯函数测试 (v79.21)。
 *
 * <p>语义镜像 {@code MaidEmojiApi}: 0=未发过视为过期; last &gt; now 时钟回退视为过期。
 */
class MaidChatBubbleApiTest {

    @Test
    @DisplayName("last=0 (未发过) → 可发")
    void shouldThrottle_neverSent_allowed() {
        assertFalse(MaidChatBubbleApi.shouldThrottle(0, 100, 600), "0=未发过, 必须放行");
    }

    @Test
    @DisplayName("时钟回退 (last > now) → 可发")
    void shouldThrottle_clockRewind_allowed() {
        assertFalse(MaidChatBubbleApi.shouldThrottle(200, 100, 600), "时间戳防残留 — 回退视为过期");
    }

    @Test
    @DisplayName("窗口内 (now - last < interval) → 节流")
    void shouldThrottle_withinWindow_throttled() {
        assertTrue(MaidChatBubbleApi.shouldThrottle(100, 150, 600), "窗口内必须节流");
        assertTrue(MaidChatBubbleApi.shouldThrottle(100, 100, 600), "同 tick 重复 → 节流");
        assertTrue(MaidChatBubbleApi.shouldThrottle(100, 699, 600), "恰在窗口内 (699-100<600) → 节流");
    }

    @Test
    @DisplayName("窗口到期 (now - last >= interval) → 可发")
    void shouldThrottle_windowExpired_allowed() {
        assertFalse(MaidChatBubbleApi.shouldThrottle(100, 700, 600), "到期边界 (700-100==600) → 放行");
        assertFalse(MaidChatBubbleApi.shouldThrottle(100, 1000, 600));
    }

    @Test
    @DisplayName("不同间隔: 触发节流 100t (5秒) / 失败节流 600t (30秒)")
    void shouldThrottle_differentIntervals() {
        assertTrue(MaidChatBubbleApi.shouldThrottle(10, 100, MaidChatBubbleApi.TRIGGER_THROTTLE_TICKS),
                "触发节流窗口内 → 节流");
        assertFalse(MaidChatBubbleApi.shouldThrottle(10, 110, MaidChatBubbleApi.TRIGGER_THROTTLE_TICKS),
                "触发节流到期 → 放行");
        assertTrue(MaidChatBubbleApi.shouldThrottle(10, 600, MaidChatBubbleApi.FAIL_THROTTLE_TICKS),
                "失败节流窗口内 → 节流");
        assertFalse(MaidChatBubbleApi.shouldThrottle(10, 610, MaidChatBubbleApi.FAIL_THROTTLE_TICKS));
    }
}
