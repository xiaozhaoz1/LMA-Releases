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
        assertEquals(13, TaskRegistryManifest.ALWAYS.size());   // v79.62.2 +dam_fill; v79.63 -smithing 锻造任务整体删除
        assertEquals(1, TaskRegistryManifest.NUMEN.size());
        assertEquals(6, TaskRegistryManifest.CREATE.size());
        assertEquals(1, TaskRegistryManifest.CBC.size());   // forge 节点恒 1 (cannon_load)
        // v79.61x 脱管线: PASSIVE 表 = 4 管线 (v79.62 snow_shovel 删 — TLM 原版清雪覆盖);
        // 纯触发型 2 (structure_sense/festival) 在 PassiveSenseRegistration.init 分派
        // (无 pipeline 占位 + PassiveDispatcher), 不在本表
        assertEquals(5, TaskRegistryManifest.PASSIVE.size());   // v79.62.5 -temp_adapt (温度删, TLM getAtBiomeTemp)
    }

    @Test
    void 清单互斥_无跨组重名() {
        Set<String> all = new HashSet<>();
        for (var s : allActive()) assertTrue(all.add(s.taskType()), "主动组重名: " + s.taskType());
        for (var s : TaskRegistryManifest.PASSIVE) assertTrue(all.add(s.taskType()), "被动与主动重名: " + s.taskType());
        assertEquals(26, all.size());   // v79.62.2 +dam_fill; v79.63 -smithing; v79.62.5 -temp_adapt
    }

    @Test
    void 组内唯一() {
        assertEquals(13, new HashSet<>(TaskRegistryManifest.ALWAYS.stream().map(TaskRegistryManifest.TaskSpec::taskType).toList()).size());   // v79.62.2 +dam_fill; v79.63 -smithing
        assertEquals(5, new HashSet<>(TaskRegistryManifest.PASSIVE.stream().map(TaskRegistryManifest.TaskSpec::taskType).toList()).size());
    }

    @Test
    void 规格构造引用非空() {
        for (var s : allActive()) assertNotNull(s.factory(), "工厂缺失: " + s.taskType());
        for (var s : TaskRegistryManifest.PASSIVE) assertNotNull(s.factory(), "工厂缺失: " + s.taskType());
    }

    // ── Drive 驱动模式 (v79.63) — 「谁 tick 我」的唯一真相源, 漏声明 = 生产静默死链 ──

    /**
     * 主动组必须 ACTIVE, 被动组必须被动驱动 — 引擎按此分派。
     * 回归防护: 2026-09-11 架构评审 P0-1 — jiuhu_milk/explorer_map 曾因**引擎侧手写集合漏名**
     * 而生产从不 tick (测试直调掩盖); 现驱动模式入表, 本测试 = 漏声明/声明错的闸门。
     */
    @Test
    void 驱动模式_分组匹配() {
        for (var s : allActive()) {
            assertEquals(TaskRegistryManifest.Drive.ACTIVE, s.drive(),
                    "主动任务必须声明 ACTIVE: " + s.taskType());
        }
        for (var s : TaskRegistryManifest.PASSIVE) {
            assertTrue(s.drive().isPassive(), "被动任务必须声明被动驱动: " + s.taskType() + " = " + s.drive());
        }
    }

    /**
     * 被动驱动**分桶固定** — 新增/改动被动必须同步本表 (刻意摩擦: 逼作者想清"谁 tick 我")。
     * 分桶口径 (用户裁定 2026-09-11): jiuhu_milk = STANDALONE (动作型, 坐下暂停);
     * explorer_map = GMPM (纯信息气泡, 不下暂停)。
     */
    @Test
    void 驱动模式_被动分桶固定() {
        var gmpm = TaskRegistryManifest.PASSIVE.stream()
                .filter(s -> s.drive() == TaskRegistryManifest.Drive.GMPM_PASSIVE)
                .map(TaskRegistryManifest.TaskSpec::taskType).sorted().toList();
        var standalone = TaskRegistryManifest.PASSIVE.stream()
                .filter(s -> s.drive() == TaskRegistryManifest.Drive.STANDALONE_PASSIVE)
                .map(TaskRegistryManifest.TaskSpec::taskType).sorted().toList();
        assertEquals(List.of("explorer_map", "haqi", "self_rescue"), gmpm,
                "GMPM 驱动桶变化 — 若是新增被动请确认它真的被 tickPassiveFor 驱动");
        assertEquals(List.of("jiuhu_milk", "torch_light"), standalone,
                "STANDALONE 驱动桶变化 — 若是新增被动请确认坐下暂停语义符合预期");
    }

    /** 驱动模式不得为 null (枚举非空, 但 record 允许传 null — 显式挡) */
    @Test
    void 驱动模式_非空() {
        for (var s : allActive()) assertNotNull(s.drive(), "drive 缺失: " + s.taskType());
        for (var s : TaskRegistryManifest.PASSIVE) assertNotNull(s.drive(), "drive 缺失: " + s.taskType());
    }
}
