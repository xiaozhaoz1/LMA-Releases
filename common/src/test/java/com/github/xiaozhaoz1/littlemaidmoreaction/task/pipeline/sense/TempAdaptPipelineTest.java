package com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.sense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TempAdaptPipeline#isCooled} 纯 JVM 测试 (v79.61x 温度取暖 CD) —
 * 取暖完成 10 分钟 CD 判定: cd<=now 或未设置 (0) → 已冷却可触发; 时钟回绕自动放行。
 */
class TempAdaptPipelineTest {

    @Test
    @DisplayName("未设置 (0) → 已冷却 (首次可触发)")
    void not_set_cooled() {
        assertTrue(TempAdaptPipeline.isCooled(0, 100));
    }

    @Test
    @DisplayName("cd 截止未到 (cd > now) → 冷却中")
    void cooling() {
        assertFalse(TempAdaptPipeline.isCooled(500, 100));
        assertFalse(TempAdaptPipeline.isCooled(12000, 11999));
    }

    @Test
    @DisplayName("cd 截止已到 (cd <= now) → 可触发 (含等值)")
    void cooled() {
        assertTrue(TempAdaptPipeline.isCooled(100, 100));
        assertTrue(TempAdaptPipeline.isCooled(100, 500));
    }

    @Test
    @DisplayName("时钟回绕 (cd 异常大/now 小) → 按 cd<=now 判, 保守放行")
    void clock_rollback() {
        assertTrue(TempAdaptPipeline.isCooled(0, 100));
    }
}
