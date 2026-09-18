package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **per-task 配置键守护** (v79.63, 用户裁定: "把 ring_interval/pos/size 这类键也纳入契约守护")。
 *
 * <p><b>为什么</b>: 配置键是**跨层契约** — 屏 (写) 与管线/服务 (读) 必须用同一个键名。
 * 写成裸字面量时, 改名/写错 = **静默失效** (玩家改了设置没反应; 编译器查不出字符串)。
 * 事故先例: `bell_ring` 的 `ring_interval` (改了不生效) · `max_products`/`enabled` 原本两处各写一遍 (v79.63 收敛为常量)。
 *
 * <p><b>规则</b>: 配置读写 API 的键参数**必须是**声明的 {@code KEY_*} 常量值 (或结构性的包封套键 {@code key}/{@code value})。
 * 允许的表: 全仓 {@code String KEY_X = "x";} 声明的值 + {@link #STRUCTURAL}。
 */
class TaskConfigKeyGuardTest {

    /** 结构性键 (包封套 / 通用容器约定), 不属于任何任务的配置面 */
    private static final Set<String> STRUCTURAL = Set.of("key", "value", "x", "y", "z");   // 包封套 + 测试夹具的 NBT 便签键

    /** 取第一个字符串实参的"配置键"API — 命中即要求其字面量 ∈ 声明集 */
    private static final Pattern[] KEY_APIS = {
            Pattern.compile("sendSetInt\\(\\s*\"([a-zA-Z0-9_]+)\""),
            Pattern.compile("sendSetList\\(\\s*\"([a-zA-Z0-9_]+)\""),
            Pattern.compile("sendSetString\\(\\s*\"([a-zA-Z0-9_]+)\""),
            Pattern.compile("sendToggle\\(\\s*\"([a-zA-Z0-9_]+)\""),
            Pattern.compile("sendRemove\\(\\s*\"([a-zA-Z0-9_]+)\""),
            Pattern.compile("\\bcfg\\.(?:put|get|contains|remove)[A-Za-z]*\\(\\s*\"([a-zA-Z0-9_]+)\""),
            Pattern.compile("getConfig\\(\\)\\.(?:put|get|contains|remove)[A-Za-z]*\\(\\s*\"([a-zA-Z0-9_]+)\""),
    };

    private static final Pattern DECLARED = Pattern.compile("(?:public static final )?String\\s+(?:KEY_|CFG_)?[A-Z0-9_]+\\s*=\\s*\"([a-zA-Z0-9_]+)\"");

    @Test
    void configKeysMustUseDeclaredConstants() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");
        Path repo = ArchSource.findMainRoot().getParent().getParent().getParent().getParent();

        List<Path> files = new ArrayList<>();
        for (String node : new String[]{"common", "forge", "neoforge"}) {
            Path src = repo.resolve(node).resolve("src").resolve(node.equals("common") ? "main/java" : "main/java");
            if (Files.isDirectory(src)) files.addAll(ArchSource.javaFiles(src));
        }
        assertTrue(files.size() > 100, "扫描文件数异常 (" + files.size() + ") — 源码根定位错了?");

        // ① 收集声明的 KEY_* 值
        Set<String> declared = new TreeSet<>();
        for (Path f : files) {
            Matcher m = DECLARED.matcher(ArchSource.read(f));
            while (m.find()) declared.add(m.group(1));
        }
        assertTrue(declared.size() >= 20,
                "声明到的 KEY_* 常量太少 (" + declared.size() + ") — 解析规则或源码结构变了?");

        // ② 收集配置 API 用到的字面量 + 违例
        List<String> violations = new ArrayList<>();
        Set<String> usedLiterals = new TreeSet<>();
        for (Path f : files) {
            String rel = f.toString().replace('\\', '/');
            String src = ArchSource.read(f);
            for (Pattern p : KEY_APIS) {
                Matcher m = p.matcher(src);
                while (m.find()) {
                    String key = m.group(1);
                    usedLiterals.add(key);
                    if (!declared.contains(key) && !STRUCTURAL.contains(key)) {
                        violations.add(rel.substring(rel.indexOf("littlemaidmoreaction/") + 21) + " → \"" + key + "\"");
                    }
                }
            }
        }

        assertTrue(usedLiterals.size() >= 5,
                "解析到的配置键太少 (" + usedLiterals.size() + ") — 检查 API 形态是否变化 (正则需同步)");
        assertTrue(violations.isEmpty(),
                "配置读写用了**裸键字面量** (屏/管线必须共用声明的 KEY_* 常量, 否则改名即静默失效):\n  "
                        + String.join("\n  ", violations)
                        + "\n  ⇒ 正确做法: 在对应管线/契约里 `public static final String KEY_X = \"x\";` 并双方引用。"
                        + " (结构性键 " + STRUCTURAL + " 已豁免)");
    }

    /** 反向自检: 解析器必须能看见已知的契约键 (防止"规则沉睡" — 正则失配后永远绿) */
    @Test
    void guardCanSeeKnownKeys() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");
        Path repo = ArchSource.findMainRoot().getParent().getParent().getParent().getParent();
        Path items = repo.resolve("common/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/task/service/ItemFilters.java");
        String src = ArchSource.read(items);
        Matcher m = DECLARED.matcher(src);
        Set<String> found = new LinkedHashSet<>();
        while (m.find()) found.add(m.group(1));
        assertTrue(found.contains("blacklist") && found.contains("whitelist"),
                "解析器看不到 ItemFilters 的 blacklist/whitelist ⇒ 守护失效, 需修解析规则 (实得: " + found + ")");
    }
}
