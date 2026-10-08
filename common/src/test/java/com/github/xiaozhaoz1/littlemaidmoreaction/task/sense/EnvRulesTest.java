package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EnvRules} 纯逻辑测试 (v79.3) — 时间段边界。
 *
 * <p>v79.62.5: 温度档 (tempCategory/CAT_*) 已删 — 温度经 TLM {@code IMaid.getAtBiomeTemp} (用户裁定不复刻)。
 */
class EnvRulesTest {

    @Test
    @DisplayName("时间段四段边界 (12000/13800/22200)")
    void timeSegment_boundaries() {
        assertEquals("DAY", EnvRules.timeSegment(0));
        assertEquals("DAY", EnvRules.timeSegment(11999));
        assertEquals("DUSK", EnvRules.timeSegment(12000));
        assertEquals("DUSK", EnvRules.timeSegment(13799));
        assertEquals("NIGHT", EnvRules.timeSegment(13800));
        assertEquals("NIGHT", EnvRules.timeSegment(22199));
        assertEquals("DAWN", EnvRules.timeSegment(22200));
        assertEquals("DAWN", EnvRules.timeSegment(23999));
    }
}
