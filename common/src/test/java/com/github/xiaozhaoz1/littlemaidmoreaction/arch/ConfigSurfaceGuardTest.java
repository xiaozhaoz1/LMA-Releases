package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **全局配置项守护** (v79.63, 用户裁定"全局配置项引用守护") — 锁住编译器管不到的配置面:
 *
 * <p>编译器能查"字段名写错"; **查不到**的是:
 * <ol>
 *   <li><b>TOML 键名</b> (字符串) — 改名 = 老存档配置**静默失效/复位** (键名是契约);</li>
 *   <li><b>平台分支漏加</b> — Stonecutter 两个分支 (`1.20.1` / `1.21.1`) 键集不一致 ⇒ 一端静默少设置;</li>
 *   <li><b>同 section 重复键</b> — 后者覆盖前者, 玩家看到的值不是预期的那个;</li>
 *   <li><b>声明了但没人读</b> — 死设置 (玩家调了没反应).</li>
 * </ol>
 *
 * <p>另: {@code docs/config-keys.lock} = **冻结键表** (由 {@code _audit/genConfigKeysLock.js} 生成) ⇒
 * 任何键的增删改都必须**有意**更新它 (并在 lessons/changelog 说明存档兼容性)。
 */
class ConfigSurfaceGuardTest {

    private static final List<String> CONFIG_FILES = List.of(
            "ActiveTaskConfig.java", "PassiveTaskConfig.java", "DefenseTowerConfig.java", "MoreActionConfig.java");

    private static Path configDir() {
        Path main = ArchSource.findMainRoot();                       // …/common/src/main/java
        return main.resolve("com/github/xiaozhaoz1/littlemaidmoreaction/config");
    }

    /** Stonecutter 分支切分: `//? if 1.20.1 {` → forge · `//?} else {` → neo · `//?}` → common */
    private static Map<String, List<String>> splitBranches(String src) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        out.put("common", new ArrayList<>());
        out.put("forge", new ArrayList<>());
        out.put("neo", new ArrayList<>());
        String mode = "common";
        for (String line : src.split("\n")) {
            String t = line.trim();
            if (t.startsWith("//? if ")) {
                mode = t.contains("1.20.1") ? "forge" : "neo";
                continue;
            }
            if (t.startsWith("//?} else {")) {
                mode = "neo";
                continue;
            }
            if (t.startsWith("//?}")) {
                mode = "common";
                continue;
            }
            out.get(mode).add(line);
        }
        return out;
    }

    /** 收集 (section 路径, 键) — 追踪 {@code b.push("x")} / {@code b.pop()} */
    private static List<String> keysOf(List<String> lines) {
        List<String> stack = new ArrayList<>();
        List<String> out = new ArrayList<>();
        Pattern push = Pattern.compile("b\\.push\\(\\s*\"([a-z0-9_.]+)\"\\s*\\)");
        Pattern def = Pattern.compile("\\.define(?:InRange|List|Enum)?\\(\\s*\"([a-z0-9_.]+)\"");
        for (String line : lines) {
            Matcher p = push.matcher(line);
            if (p.find()) stack.add(p.group(1));
            if (line.contains("b.pop()") && !stack.isEmpty()) stack.remove(stack.size() - 1);
            Matcher d = def.matcher(line);
            if (d.find()) out.add(String.join(".", stack) + "|" + d.group(1));
        }
        return out;
    }

    @Test
    void platformBranchesHaveIdenticalKeySets() {
        Path dir = configDir();
        Assumptions.assumeTrue(Files.isDirectory(dir), "非源码树环境");
        List<String> problems = new ArrayList<>();
        for (String f : CONFIG_FILES) {
            Path p = dir.resolve(f);
            assertTrue(Files.isRegularFile(p), "找不到配置类: " + p);
            Map<String, List<String>> br = splitBranches(ArchSource.read(p));
            Set<String> common = new LinkedHashSet<>(keysOf(br.get("common")));
            Set<String> forge = new LinkedHashSet<>(keysOf(br.get("forge")));
            Set<String> neo = new LinkedHashSet<>(keysOf(br.get("neo")));
            Set<String> forgeAll = new LinkedHashSet<>(common);
            forgeAll.addAll(forge);
            Set<String> neoAll = new LinkedHashSet<>(common);
            neoAll.addAll(neo);
            Set<String> onlyForge = new TreeSet<>(forgeAll);
            onlyForge.removeAll(neoAll);
            Set<String> onlyNeo = new TreeSet<>(neoAll);
            onlyNeo.removeAll(forgeAll);
            if (!onlyForge.isEmpty() || !onlyNeo.isEmpty()) {
                problems.add(f + ": 仅 1.20.1=" + onlyForge + " 仅 1.21.1=" + onlyNeo);
            }
        }
        assertTrue(problems.isEmpty(),
                "平台分支键集不一致 ⇒ 某一端静默少设置 (新设置必须写进两个 //? 分支):\n  " + String.join("\n  ", problems));
    }

    @Test
    void noDuplicateKeyWithinSameSection() {
        Path dir = configDir();
        Assumptions.assumeTrue(Files.isDirectory(dir), "非源码树环境");
        List<String> problems = new ArrayList<>();
        for (String f : CONFIG_FILES) {
            Map<String, List<String>> br = splitBranches(ArchSource.read(dir.resolve(f)));
            for (Map.Entry<String, List<String>> e : br.entrySet()) {
                Set<String> seen = new LinkedHashSet<>();
                for (String k : keysOf(e.getValue())) {
                    if (!seen.add(k)) problems.add(f + " [" + e.getKey() + "] 重复键: " + k);
                }
            }
        }
        assertTrue(problems.isEmpty(), "同 section 出现重复键 (后者覆盖前者, 玩家值不是预期的):\n  "
                + String.join("\n  ", problems));
    }

    @Test
    void everyDeclaredConfigValueIsActuallyUsed() {
        Path dir = configDir();
        Assumptions.assumeTrue(Files.isDirectory(dir), "非源码树环境");
        Pattern field = Pattern.compile(
                "public static final (?:ForgeConfigSpec|ModConfigSpec)\\.\\w+\\s+([A-Z][A-Z0-9_]*)\\s*=");
        List<String> problems = new ArrayList<>();
        for (String f : CONFIG_FILES) {
            String src = ArchSource.read(dir.resolve(f));
            Matcher m = field.matcher(src);
            while (m.find()) {
                String name = m.group(1);
                // 声明行算 1 次; 被任何代码读过 ⇒ ≥2
                int count = 0;
                Matcher c = Pattern.compile("\\b" + name + "\\b").matcher(src);
                while (c.find()) count++;
                if (count <= 1) problems.add(f + ": " + name + " (声明后无人引用)");
            }
        }
        assertTrue(problems.isEmpty(),
                "存在\"死配置\"(玩家调了没反应) — 要么接上读取点, 要么删掉声明:\n  " + String.join("\n  ", problems));
    }

    @Test
    void configKeysMatchFrozenLock() {
        Path main = ArchSource.findMainRoot();
        Assumptions.assumeTrue(main != null, "非源码树环境");
        Path repo = main.getParent().getParent().getParent().getParent();
        Path lock = repo.resolve("docs/config-keys.lock");
        assertTrue(Files.isRegularFile(lock), "缺少配置冻结表: " + lock
                + " (跑: node _audit/genConfigKeysLock.js)");

        // 当前键集 (分支合并去重) — 与生成脚本同构
        Set<String> actual = new TreeSet<>();
        for (String f : CONFIG_FILES) {
            String cls = f.replace(".java", "");
            Map<String, List<String>> br = splitBranches(ArchSource.read(configDir().resolve(f)));
            for (List<String> lines : br.values()) {
                for (String k : keysOf(lines)) actual.add(cls + "|" + k);
            }
        }
        // 冻结表 (跳过注释/空行)
        Set<String> expected = new TreeSet<>();
        for (String line : ArchSource.read(lock).split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            expected.add(t);
        }

        Set<String> added = new TreeSet<>(actual);
        added.removeAll(expected);
        Set<String> removed = new TreeSet<>(expected);
        removed.removeAll(actual);
        assertEquals(expected, actual,
                "配置键面与冻结表不一致 —\n  新增键: " + added + "\n  消失键: " + removed
                        + "\n  ⇒ 若是有意改动: ① 确认老存档兼容 ② 跑 `node _audit/genConfigKeysLock.js` 更新 docs/config-keys.lock"
                        + " ③ 记入 docs/lessons-learned.md / changelog。");
    }
}
