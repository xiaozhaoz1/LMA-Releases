package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LMAT.Task 简单任务模板契约单测 (v79.61x 测试补强) — 纯 JVM (只构造 + 调纯默认方法,
 * 不触碰 MC 类型, 错题 #174 铁律; LmatSimpleTest 同模式实证过)。
 */
class LmatTaskTest {

    static final class X extends LMAT.Task {
        X() { super("ext_x"); }
    }

    @Test
    void 类型名往返() {
        assertEquals("ext_x", new X().taskType());
    }

    @Test
    void 默认契约全来自接口() {
        TaskPipeline p = new X();
        assertEquals(0, p.priority());
        assertEquals(false, p.isLongRunning());
        assertEquals(true, p.steps().isEmpty());
        assertEquals(false, p.workPointTask());
    }

    @Test
    void 默认验证通过() {
        assertEquals(true, new X().validate(null, null, null).completed());
    }

    @Test
    void 默认tick为空操作() {
        TaskPipeline p = new X();
        p.tick(null, null);
        assertTrue(true, "默认 tick 零行为不抛");
    }
}
