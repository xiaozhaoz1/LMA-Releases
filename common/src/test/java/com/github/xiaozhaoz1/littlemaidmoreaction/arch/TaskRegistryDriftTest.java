package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务清单一致性守护 (v79.63 B3) — 防"新增任务忘了改某个映射表"的静默缺失。
 *
 * <p><b>背景 (2026-09-11 架构审计实测)</b>: 任务清单在 9 处各有硬编码副本, 只有
 * {@code TaskRegistryManifest.Drive} 有启动期 fail-fast。审计逐处实证后**分成两类** —
 * 只有"用户可见且缺项=缺陷"的表才断言完整; 其余是特例分支/默认值, **缺项合法**:
 *
 * <table border="1">
 *   <tr><th>表</th><th>类别</th><th>实证理由</th></tr>
 *   <tr><td>{@code LmaTaskProgressDisplay.friendlyName}</td><td><b>必须全覆盖</b></td>
 *       <td>气泡文案; 缺项 → 气泡直接露原始 id (审计实测曾缺 13/26 + 孤儿 brewing)</td></tr>
 *   <tr><td>{@code TaskGroup.loadDefaults}</td><td>子集合法</td><td>起步分组仅 4 任务, 用户可在 JSON 自加</td></tr>
 *   <tr><td>{@code ActiveTaskConfig}</td><td>子集合法</td><td>只有"可配置"任务才有配置项</td></tr>
 *   <tr><td>{@code LmaTaskTypeRegistry.SIMPLE_TASKS}</td><td>子集合法</td><td>分类集合; 缺省 = 复杂任务</td></tr>
 *   <tr><td>{@code TaskDispatcher} / {@code NearbyBlockScanner} / {@code LmaTypedFlowTask}
 *       / {@code TaskSettingsScreen}</td><td>子集合法</td><td>特例分支 (switch/if), 不命中即走通用路径</td></tr>
 * </table>
 *
 * <p>新表加入时: 先判"缺项是缺陷还是合法默认", 前者加进 {@link #MUST_COVER} 并补全, 后者加进
 * {@link #SUBSET_ONLY} 留痕 (避免下一个人重新考古)。
 */
class TaskRegistryDriftTest {

    private static final String MANIFEST = "task/TaskRegistryManifest.java";

    /** 必须覆盖全部任务类型的表: 相对路径 → 缺项后果说明 */
    private static final Map<String, String> MUST_COVER = new LinkedHashMap<>();

    /** 缺项合法的表 (特例分支/默认值): 相对路径 → 为何合法 (留痕, 不参与断言) */
    private static final Map<String, String> SUBSET_ONLY = new LinkedHashMap<>();

    static {
        MUST_COVER.put("task/runtime/LmaTaskProgressDisplay.java",
                "friendlyName 中文名 — 缺项 → 进度/完成气泡露原始 id (用户可见)");
        SUBSET_ONLY.put("task/gui/TaskGroup.java", "起步分组 (仅 4 任务, 用户可在 task_groups.json 自加)");
        SUBSET_ONLY.put("config/ActiveTaskConfig.java", "仅可配置任务有配置项");
        SUBSET_ONLY.put("adapter/LmaTaskTypeRegistry.java", "SIMPLE_TASKS 分类集合 (缺省 = 复杂任务)");
        SUBSET_ONLY.put("task/runtime/TaskDispatcher.java", "特例分支");
        SUBSET_ONLY.put("ai/scanner/NearbyBlockScanner.java", "特例分支");
        SUBSET_ONLY.put("adapter/LmaTypedFlowTask.java", "特例分支");
        SUBSET_ONLY.put("screen/TaskSettingsScreen.java", "特例分支");
    }

    @Test
    @DisplayName("friendlyName 必须覆盖全部注册任务 (双向: 不缺 + 无孤儿)")
    void friendlyNameCoversAllTasks() {
        Path pkgRoot = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(pkgRoot != null, "未找到 common/src/main/java — 跳过");

        List<String> tasks = ArchSource.taskSpecLiterals(ArchSource.read(pkgRoot.resolve(MANIFEST)));
        assertTrue(tasks.size() >= 20, "manifest 解析异常 (任务数 " + tasks.size() + ") — 检查 ArchSource.taskSpecLiterals 模式");

        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : MUST_COVER.entrySet()) {
            Path f = pkgRoot.resolve(e.getKey());
            Assumptions.assumeTrue(Files.isRegularFile(f), "缺少 " + e.getKey() + " — 跳过");
            List<String> cases = ArchSource.switchCaseLiterals(ArchSource.methodBody(ArchSource.read(f), "friendlyName"));
            covered.addAll(cases);
            for (String t : tasks) {
                if (!cases.contains(t)) missing.add(e.getKey() + " 缺: " + t + "  (" + e.getValue() + ")");
            }
        }
        assertTrue(missing.isEmpty(),
                "任务清单漂移 — 新增任务后忘记补映射表 (缺项即用户可见缺陷):\n  " + String.join("\n  ", missing));

        // 反向: 映射表里有、manifest 没有 → 孤儿 (任务删了/改名了, 表没跟着改)
        List<String> orphans = new ArrayList<>();
        for (String c : covered) {
            if (!tasks.contains(c)) orphans.add(c);
        }
        assertTrue(orphans.isEmpty(),
                "映射表存在**孤儿条目** (manifest 无此任务 — 任务已删/改名, 表未同步):\n  " + String.join("\n  ", orphans));
    }

    @Test
    @DisplayName("子集表留痕完整 (新表必须显式归类, 不得沉默漏掉)")
    void subsetTablesAreDocumented() {
        assertEquals(1, MUST_COVER.size(), "MUST_COVER 应只有 friendlyName (见类 javadoc 实证表)");
        assertEquals(7, SUBSET_ONLY.size(), "SUBSET_ONLY 应有 7 项 (审计实证的特例表)");
    }
}
