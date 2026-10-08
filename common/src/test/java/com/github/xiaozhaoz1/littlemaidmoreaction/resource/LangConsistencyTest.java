package com.github.xiaozhaoz1.littlemaidmoreaction.resource;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * lang 双文件对称守卫 (2026-08-11c 批次 C) — zh_cn.json / en_us.json key 集完全对称。
 *
 * <p>规则: 新增 lang key 必须双文件同改 (含 task.littlemaidmoreaction.&lt;type&gt; 任务名,
 * 配合 LMAT.registerTask 聚合助手)。单边新增/删除/拼写偏差 → 测试红。
 *
 * <p>纯 JVM: 仅 classpath 资源 + Gson (MC 依赖自带), 零 MC 类加载, 不触发任何主类 clinit。
 * 资源可达性: forge 节点 test runtimeClasspath 继承 main.runtimeClasspath
 * (forge/build.gradle L63-64), main resources 含合并后的 assets/littlemaidmoreaction/lang/*.json
 * (multiloader 插件合并 common 资源) — 不可达时 assertNotNull 报清晰路径错误,
 * 兜底方案: 改文件路径注入 (@TempDir 拷贝, 见 FestivalLoaderTest 同款)。
 *
 * <p>task↔TaskRegistry.taskTypes() 交叉一致未做: TaskRegistry clinit 含
 * ModList.get().isLoaded(...) 链 (CompatToggle→NumenCompat→ModList, 纯 JVM 必 NPE,
 * 错题 #173/#174 同族) — 交叉核对降级为任务 lang key 格式正则 + 双文件对称守卫;
 * 全量交叉建议后续在 gametest 补断言 (批次报告说明)。
 */
class LangConsistencyTest {

    private static final Gson GSON = new Gson();

    private static final String LANG_PATH = "/assets/littlemaidmoreaction/lang/";

    /** 任务 lang key 格式: task.littlemaidmoreaction.<type> (type = 小写字母/数字/下划线) */
    private static final Pattern TASK_KEY_PATTERN = Pattern.compile("task\\.littlemaidmoreaction\\.[a-z0-9_]+");

    /** 加载 lang JSON → key→value 表 (资源缺失 → 断言失败并给出修复方向) */
    @SuppressWarnings("unchecked")
    private static Map<String, String> load(String file) {
        InputStream in = LangConsistencyTest.class.getResourceAsStream(LANG_PATH + file);
        assertNotNull(in, "lang 资源必须在测试类路径: " + file
                + " — forge 节点 test runtimeClasspath 继承 main resources; 若仍不可达改路径注入");
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return (Map<String, String>) GSON.fromJson(r, Map.class);
        } catch (java.io.IOException e) {
            throw new RuntimeException("lang 资源读取失败: " + file, e);
        }
    }

    @Test
    @DisplayName("zh/en key 集完全对称 (无单边缺失/多余)")
    void zh_en_key_symmetry() {
        Map<String, String> zh = load("zh_cn.json");
        Map<String, String> en = load("en_us.json");

        Set<String> onlyZh = new LinkedHashSet<>(zh.keySet());
        onlyZh.removeAll(en.keySet());
        Set<String> onlyEn = new LinkedHashSet<>(en.keySet());
        onlyEn.removeAll(zh.keySet());

        assertEquals(Set.of(), onlyZh, "zh 独有 key (en 缺): " + onlyZh);
        assertEquals(Set.of(), onlyEn, "en 独有 key (zh 缺): " + onlyEn);
    }

    @Test
    @DisplayName("任务 lang key 格式合法且双文件各有任务键")
    void task_key_format() {
        Map<String, String> zh = load("zh_cn.json");
        Map<String, String> en = load("en_us.json");

        Set<String> zhTasks = new LinkedHashSet<>();
        Set<String> enTasks = new LinkedHashSet<>();
        for (String k : zh.keySet()) {
            if (k.startsWith("task.littlemaidmoreaction.")) {
                assertTrue(TASK_KEY_PATTERN.matcher(k).matches(), "任务 key 格式非法: " + k);
                zhTasks.add(k);
            }
        }
        for (String k : en.keySet()) {
            if (k.startsWith("task.littlemaidmoreaction.")) {
                assertTrue(TASK_KEY_PATTERN.matcher(k).matches(), "任务 key 格式非法: " + k);
                enTasks.add(k);
            }
        }
        assertFalse(zhTasks.isEmpty(), "zh_cn 至少应有 1 个任务 key");
        assertEquals(zhTasks, enTasks, "双文件任务 key 集必须一致 (新增任务双文件同改)");
    }

    @Test
    @DisplayName("无空翻译值 (value 非空白)")
    void no_blank_values() {
        Map<String, String> zh = load("zh_cn.json");
        Map<String, String> en = load("en_us.json");
        zh.forEach((k, v) -> assertFalse(v == null || v.isBlank(), "zh 空值: " + k));
        en.forEach((k, v) -> assertFalse(v == null || v.isBlank(), "en 空值: " + k));
    }
}
