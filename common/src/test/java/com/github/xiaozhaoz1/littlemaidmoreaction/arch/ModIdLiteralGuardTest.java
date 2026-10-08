package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **modId 字面量守护** (v79.63, 采纳清单 ⑤)。
 *
 * <p><b>为什么</b>: 同类附属踩过"事件订阅写旧 modId (`maid_smart` ≠ `promaid`) ⇒ 注入从未生效"的坑
 * (见 Promaid 机制详解)。modId 写错**不会报错**, 只会静默失效 —— 必须先靠单一来源 + 机械守护挡住。
 *
 * <p><b>规则</b>: 全仓 Java 源码里 <b>不得</b>出现字面量 {@code "littlemaidmoreaction"},
 * 唯一允许处 = {@code LittleMaidMoreAction.MOD_ID} 的定义本身; 其余一律引用常量。
 * (注: lang key {@code "itemGroup.littlemaidmoreaction"} 是**另一个**字面量, 不受本规则约束 —— 那是翻译键契约。)
 */
class ModIdLiteralGuardTest {

    private static final String LITERAL = "\"littlemaidmoreaction\"";
    private static final Pattern LINE = Pattern.compile(".*\\Q" + LITERAL + "\\E.*");

    @Test
    void modIdLiteralOnlyInItsOwnDefinition() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");
        Path repo = ArchSource.findMainRoot().getParent().getParent().getParent().getParent();

        List<Path> files = new ArrayList<>();
        files.addAll(ArchSource.javaFiles(root));                                  // common/src/main/java/...
        for (String node : new String[]{"forge", "neoforge"}) {
            Path src = repo.resolve(node).resolve("src").resolve("main").resolve("java");
            if (Files.isDirectory(src)) files.addAll(ArchSource.javaFiles(src));
        }

        List<String> violations = new ArrayList<>();
        int scanned = 0;
        for (Path f : files) {
            String rel = f.toString().replace('\\', '/');
            if (!rel.endsWith(".java")) continue;
            scanned++;
            String src = ArchSource.read(f);
            if (!src.contains(LITERAL)) continue;
            Matcher m = LINE.matcher(src);
            while (m.find()) {
                String line = m.group().trim();
                // 唯一豁免: MOD_ID 的定义行
                if (line.contains("String MOD_ID =")) continue;
                violations.add(rel.substring(rel.indexOf("littlemaidmoreaction/") + 21) + " → " + line);
            }
        }

        assertTrue(scanned > 100, "扫描文件数异常 (" + scanned + ") — 源码根定位错了?");
        assertTrue(violations.isEmpty(),
                "发现硬编码 modId 字面量 (必须改用 LittleMaidMoreAction.MOD_ID; 写出错 = 事件/注册静默失效):\n  "
                        + String.join("\n  ", violations));
    }
}
