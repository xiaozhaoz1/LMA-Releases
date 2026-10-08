package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守护测试的**自身健康**守护（错题 #369 ✓）
 *
 * <p><b>事故</b>：{@code GameTestTimeoutGuardTest} 写死找 {@code gametest/LmaGameTests.java} ✗，
 * 而该文件**早已被拆成** {@code LmaCore/LmaHarvest/LmaPassive/LmaCompat} ✗ ⇒
 * 它的 {@code Assumptions.assumeTrue(源码树不可见 ⇒ 跳过)} **每轮静默跳过** ✗ ⇒
 * **等于从来没有守过** ✓（假绿 ✓）。
 *
 * <p><b>本守护要做的</b>：把"守护引用的源文件是否存在"变成**机器可查** ✓ ——
 * 凡是在测试里写死了 {@code "Xxx.java"} 这类**文件名**，它**必须**能在本仓库的任一源根里找到 ✓；
 * 找不到 ⇒ **直接失败** ✗（而不是等某个守护静默跳过 ✓）。
 *
 * <p>⚠ 只校验"文件名形态的字面量" ✓；对**过滤器**用法（如 {@code name.endsWith("Screen.java")} ✗）
 * 与**运行时临时文件**（测试自己写到 tmp 的名字 ✓）自动跳过 ✓，避免误报 ✓。
 */
class ArchGuardHealthTest {

    /** 仓库里允许出现源文件的位置（含平台源集 ✓） */
    private static final List<String> SOURCE_ROOTS = List.of(
            "common/src/main/java", "common/src/test/java",
            "forge/src/main/java", "neoforge/src/main/java",
            "fabric/src/main/java");

    private static final Pattern FILE_LITERAL = Pattern.compile(String.valueOf((char) 34)
            + "([A-Za-z0-9_]+\\.java)" + String.valueOf((char) 34));

    private static final List<String> NOT_A_TARGET = List.of(
            "endsWith(", "contains(", "startsWith(", "matches(", "equals(", "equalsIgnoreCase(",
            "resolve(", "compareTo(", "indexOf(", "lastIndexOf(", "filter(");

    @Test
    @DisplayName("守护/测试引用的 .java 文件名必须真实存在（防止改名/拆分后静默失效）")
    void referencedSourceFilesExist() throws Exception {
        Path pkgRoot = ArchSource.findPackageRoot();
        assertTrue(pkgRoot != null, "定位包根失败 ⇒ 守护失效, 必须修 ✗");

        // 仓库根: 从主源根向上找 settings.gradle.kts ✓（不靠数上跳级数 ✗ —— 我在本类里已踩过一次 ✓）
        Path repo = ArchSource.findMainRoot();
        while (repo != null && !Files.isRegularFile(repo.resolve("settings.gradle.kts"))) repo = repo.getParent();
        assertTrue(repo != null, "仓库根定位失败 ⇒ 守护失效 ✗");

        Path testRoot = repo.resolve("common/src/test/java");
        assertTrue(Files.isDirectory(testRoot), "测试源根不存在: " + testRoot + " ⇒ 守护失效 ✗");

        // 收集仓库里所有 .java 文件名（用于"是否存在"判定 ✓）
        Set<String> existing = new LinkedHashSet<>();
        for (String rel : SOURCE_ROOTS) {
            Path r = repo.resolve(rel);
            if (!Files.isDirectory(r)) continue;
            for (Path f : ArchSource.javaFiles(r)) existing.add(f.getFileName().toString());
        }
        assertTrue(existing.size() > 300, "收集到的源文件数异常少（" + existing.size() + "）⇒ 守护失效 ✗");

        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (Path f : ArchSource.javaFiles(testRoot)) {
            String text = ArchSource.read(f);
            Matcher m = FILE_LITERAL.matcher(text);
            List<String> lines = text.lines().toList();
            while (m.find()) {
                String name = m.group(1);
                int at = m.start();
                // ⚠ 注释行跳过 ✓ —— 本类自己的说明、以及 GameTestTimeoutGuardTest 的历史注记里都会提到旧文件名 ✓
                int lineNo = 1 + (int) text.substring(0, at).chars().filter(c -> c == '\n').count();
                String src = lines.size() >= lineNo ? lines.get(lineNo - 1).trim() : "";
                if (src.startsWith("//") || src.startsWith("*") || src.startsWith("/*")) continue;
                String before = text.substring(Math.max(0, at - 24), at);
                boolean isFilterOrTemp = NOT_A_TARGET.stream().anyMatch(before::contains);
                if (isFilterOrTemp) continue;
                if (name.contains("Guard") || name.contains("Test")) continue;   // 测试类自身引用 ✓
                checked++;
                if (!existing.contains(name)) {
                    offenders.add(ArchSource.rel(testRoot, f) + " 引用了不存在的源文件: " + name);
                }
            }
        }
        assertTrue(checked >= 5, "实际校验的引用异常少（" + checked + "）⇒ 守护自身可能失效, 必须修 ✗");
        assertTrue(offenders.isEmpty(),
                "有测试引用了**不存在**的源文件 ✗ —— 这类路径失效会让对应守护静默跳过 ⇒ 假绿 ✓\n"
                        + "修法: 改成扫描目录（不写死文件名 ✓），或把该守护改成 fail-closed ✓\n违规点:\n  "
                        + String.join("\n  ", offenders));
    }
}
