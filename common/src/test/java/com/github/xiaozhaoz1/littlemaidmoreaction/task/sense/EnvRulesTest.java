package com.github.xiaozhaoz1.littlemaidmoreaction.task.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EnvRules} 纯逻辑测试 (v79.3) — 温度档/时间段边界。
 */
class EnvRulesTest {

    @Test
    @DisplayName("温度四档边界 (0.15/0.55/0.95)")
    void tempCategory_boundaries() {
        assertEquals("COLD", EnvRules.tempCategory(0.1499f));
        assertEquals("COLD", EnvRules.tempCategory(0.0f));
        assertEquals("OCEAN", EnvRules.tempCategory(0.15f), "恰 0.15 → OCEAN (>= 语义)");
        assertEquals("OCEAN", EnvRules.tempCategory(0.5499f));
        assertEquals("MEDIUM", EnvRules.tempCategory(0.55f));
        assertEquals("MEDIUM", EnvRules.tempCategory(0.9499f));
        assertEquals("WARM", EnvRules.tempCategory(0.95f));
        assertEquals("WARM", EnvRules.tempCategory(2.0f));
    }

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
