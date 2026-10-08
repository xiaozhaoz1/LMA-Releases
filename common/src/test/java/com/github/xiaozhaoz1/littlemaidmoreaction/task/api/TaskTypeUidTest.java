package com.github.xiaozhaoz1.littlemaidmoreaction.task.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TaskTypeUid 纯函数单测 (v79.61x 测试补强) — uid 净化/反解契约, 纯 JVM (错题 #174 铁律)。
 * 净化规则 = 外部注册命名约定的实现真相 (LMAT javadoc「净化撞 uid」)。
 */
class TaskTypeUidTest {

    @Test
    void 小写化() {
        assertEquals("mytask", TaskTypeUid.sanitize("MyTask"));
        assertEquals("my_task", TaskTypeUid.sanitize("MY_TASK"));
    }

    @Test
    void 非常规字符换下划线() {
        assertEquals("my_task", TaskTypeUid.sanitize("my task"));
        assertEquals("__", TaskTypeUid.sanitize("任务"));
        assertEquals("a_b", TaskTypeUid.sanitize("a$b"));
        assertEquals("__", TaskTypeUid.sanitize("!!"));
    }

    @Test
    void 合法字符保留() {
        assertEquals("my-task.v1", TaskTypeUid.sanitize("my-task.v1"));
        assertEquals("a/b", TaskTypeUid.sanitize("a/b"));
    }

    @Test
    void 空值兜底() {
        assertEquals("unknown", TaskTypeUid.sanitize(""));
    }

    @Test
    void 反解任务path() {
        assertEquals("craft_chain", TaskTypeUid.extractTaskType("task/craft_chain"));
        assertEquals("craft_chain", TaskTypeUid.extractTaskType("lma:task/craft_chain"));
        assertEquals("flow_task", TaskTypeUid.extractTaskType("flow_task"));
    }

    @Test
    void 反解非任务path() {
        assertNull(TaskTypeUid.extractTaskType(null));
        assertNull(TaskTypeUid.extractTaskType("other"));
        assertNull(TaskTypeUid.extractTaskType(""));
    }
}
