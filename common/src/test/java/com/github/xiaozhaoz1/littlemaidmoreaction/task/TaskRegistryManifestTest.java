package com.github.xiaozhaoz1.littlemaidmoreaction.task;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务注册规格表单测 (v79.61 架构批 3c C3 → 规格化) — 纯 JVM (规格零 MC 依赖, 构造引用惰性, 错题 #174 铁律)。
 */
class TaskRegistryManifestTest {

    private static List<TaskRegistryManifest.TaskSpec> allActive() {
        return Stream.of(TaskRegistryManifest.ALWAYS, TaskRegistryManifest.NUMEN,
                        TaskRegistryManifest.CREATE, TaskRegistryManifest.CBC)
                .flatMap(List::stream).toList();
    }

    @Test
    void 清单计数_对齐实证口径() {
        // v79.62.1: +campfire 篝火烤食物 + void_excavation 挖空置域 (主动)
        assertEquals(14, TaskRegistryManifest.ALWAYS.size());   // v79.62.2 +dam_fill
        assertEquals(1, TaskRegistryManifest.NUMEN.size());
        assertEquals(6, TaskRegistryManifest.CREATE.size());
        assertEquals(1, TaskRegistryManifest.CBC.size());   // forge 节点恒 1 (cannon_load)
        // v79.61x 脱管线: PASSIVE 表 = 4 管线 (v79.62 snow_shovel 删 — TLM 原版清雪覆盖);
        // 纯触发型 2 (structure_sense/festival) 在 PassiveSenseRegistration.init 分派
        // (无 pipeline 占位 + PassiveDispatcher), 不在本表
        assertEquals(6, TaskRegistryManifest.PASSIVE.size());
    }

    @Test
    void 清单互斥_无跨组重名() {
        Set<String> all = new HashSet<>();
        for (var s : allActive()) assertTrue(all.add(s.taskType()), "主动组重名: " + s.taskType());
        for (var s : TaskRegistryManifest.PASSIVE) assertTrue(all.add(s.taskType()), "被动与主动重名: " + s.taskType());
        assertEquals(28, all.size());   // v79.62.2 +dam_fill
    }

    @Test
    void 组内唯一() {
        assertEquals(14, new HashSet<>(TaskRegistryManifest.ALWAYS.stream().map(TaskRegistryManifest.TaskSpec::taskType).toList()).size());   // v79.62.2 +dam_fill
        assertEquals(6, new HashSet<>(TaskRegistryManifest.PASSIVE.stream().map(TaskRegistryManifest.TaskSpec::taskType).toList()).size());
    }

    @Test
    void 规格构造引用非空() {
        for (var s : allActive()) assertNotNull(s.factory(), "工厂缺失: " + s.taskType());
        for (var s : TaskRegistryManifest.PASSIVE) assertNotNull(s.factory(), "工厂缺失: " + s.taskType());
    }
}
