package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 面向玩家文本**零容忍 i18n 守护** (v79.63.7 — 错题 #328)。
 *
 * <p>规则: 气泡/玩家消息调用里的文案**必须**走 {@code Component.translatable(...)} (或 {@code .getString()} 包装的
 * translatable), **不得**出现中文字面量 ✗。理由: 硬编码中文在英文客户端不显示/不翻译, 且多人协作时无法统一校对 ✓。
 *
 * <p>覆盖面 (与清扫时同一口径): {@code MaidChatBubbleApi.show*}, 各管线的 {@code .bubble(...)},
 * 以及 {@code displayClientMessage / sendSystemMessage / sendMessage} ✓。注释与 javadoc 不算 ✗ (只查代码行 ✓)。
 */
class PlayerTextI18nGuardTest {

    /** 面向玩家的调用形态 (取第一段参数) */
    private static final List<Pattern> CALLS = List.of(
            Pattern.compile("(?:MaidChatBubbleApi\\.(?:showInfo|showFail|showComplete|showTrigger|showProgress)|\\.bubble)\\s*\\(([^;]{0,200})"),
            Pattern.compile("(?:displayClientMessage|sendSystemMessage|sendMessage)\\s*\\(([^;]{0,200})"));

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    @Test
    void noHardcodedChineseInPlayerFacingText() throws IOException {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(Files.isDirectory(root), "源码树不可见 (打包环境) — 跳过");

        List<String> bad = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String trimmed = line.trim();
                    if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                        continue;   // 注释/javadoc 不算 ✗
                    }
                    for (Pattern p : CALLS) {
                        Matcher m = p.matcher(line);
                        while (m.find()) {
                            scanned++;
                            String arg = m.group(1);
                            if (CJK.matcher(arg).find() && !arg.contains("translatable")) {
                                bad.add(root.relativize(f).toString().replace('\\', '/') + ":" + (i + 1)
                                        + " → " + arg.substring(0, Math.min(80, arg.length())));
                            }
                        }
                    }
                }
            }
        }
        assertTrue(scanned >= 20, "应扫到 ≥20 处面向玩家的调用 (口径失效?), 实际 " + scanned);
        assertTrue(bad.isEmpty(),
                "面向玩家的文案必须是 Component.translatable(...) — 发现硬编码中文 (英文客户端无法翻译): " + bad);
    }
}
