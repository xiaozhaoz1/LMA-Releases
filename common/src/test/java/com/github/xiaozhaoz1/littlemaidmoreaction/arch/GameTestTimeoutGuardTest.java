package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
// (Assumptions 已移除 ✗) —— 原来用 Assumptions.assumeTrue(源码树不可见 ⇒ 跳过) ✗ ⇒ 守护**静默失效** ✓
// 真相: 该守护写死找 gametest/LmaGameTests.java ✗, 而它**早已被拆成** LmaCore/LmaHarvest/LmaPassive/LmaCompat ✗
//       ⇒ 文件找不到 ⇒ 每轮静默跳过 ⇒ 相当于**从来没有守过** ✓（错题口径: 不能静默跳过 ✓）
// 现改为 fail-closed: 目录/文件缺失 ⇒ 直接断言失败 ✓, 并扫描**全部** gametest 源 ✓

/**
 * gametest **时序自洽**守护 (v79.63.6 — 错题 #326 的直接教训)。
 *
 * <p>踩过的坑: 把 {@code runAfterDelay(600)} 的等待放宽, 却忘了抬 {@code @GameTest(timeoutTicks=400)}
 * ⇒ 用例**每轮必红** ✗ (而且看起来像"负载抖动", 极易误诊 ✓)。本测试机械校验:
 * <b>每条用例的最大等待必须 ≤ timeoutTicks - 10</b> (留出断言执行余量)。
 *
 * <p>第二个用例守护**批次隔离**: 时间/寻路敏感的家族 (挖矿 z_ore / 农家·刷子·熔炉寻路 z_slow)
 * 必须待在各自独占批次里, 免得被 defaultBatch 的并发挤到超时 (抖动根源 ✓)。
 */
class GameTestTimeoutGuardTest {

    /** gametest 源**目录**（可能被拆成多个文件 ✓ —— 不再写死单文件名 ✗） */
    private static Path testDir() {
        return ArchSource.findPackageRoot().resolve("gametest");
    }

    /**
     * 读**全部** gametest 源并拼接（按文件名排序 ✓）。
     *
     * <p>⚠ fail-closed ✓：目录不存在/没有 java 源 ⇒ **断言失败** ✗（绝不跳过 ✗）——
     * 这正是本测试此前"静默失效"的教训 ✓：写死文件名 + assumeTrue 跳过 ⇒ 文件被拆分后无声无息失效 ✓
     */
    private static String readAllTestSources() {
        Path dir = testDir();
        assertTrue(Files.isDirectory(dir), "gametest 源目录不存在: " + dir + " ⇒ 守护失效, 必须修 ✗");
        List<Path> files = ArchSource.javaFiles(dir);
        assertTrue(!files.isEmpty(), "gametest 下没有任何 java 源: " + dir + " ⇒ 守护失效, 必须修 ✗");
        StringBuilder sb = new StringBuilder();
        files.stream().sorted().forEach(p -> sb.append(ArchSource.read(p)).append('\n'));
        return sb.toString();
    }
    /** 断言执行余量 (tick) — 等待触发后 lambda 还要跑断言/写日志 */
    private static final int MARGIN = 10;

    private static final Pattern TEST_ANNOTATION = Pattern.compile(
            "@GameTest\\(([\\s\\S]{0,300}?)\\)\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*public static void (\\w+)\\s*\\(");

    @Test
    void everyDelayFitsWithinTimeout() throws IOException {
        String src = readAllTestSources();
        Matcher m = TEST_ANNOTATION.matcher(src);
        List<String> bad = new ArrayList<>();
        int checked = 0;
        while (m.find()) {
            String ann = m.group(1);
            String name = m.group(2);
            Matcher to = Pattern.compile("timeoutTicks\\s*=\\s*(\\d+)").matcher(ann);
            int timeout = to.find() ? Integer.parseInt(to.group(1)) : 100;   // gametest 默认 100t
            int start = m.end();
            int next = src.indexOf("@GameTest", start);
            String body = src.substring(start, next < 0 ? src.length() : next);
            int maxDelay = 0;   // 单条最大值 (仅记录)
            int sumDelay = 0;    // v79.63.6: 嵌套/串行延迟累加 — 才是真实耗时路径 ✓
            Matcher d = Pattern.compile("runAfterDelay\\((\\d+)L?").matcher(body);
            while (d.find()) {
                int v = Integer.parseInt(d.group(1));
                maxDelay = Math.max(maxDelay, v);
                sumDelay += v;
            }
            checked++;
            // 累加值才是真实占用 (单条最大只是下限); 两者都必须在超时内 ✓
            if (sumDelay + MARGIN > timeout) {
                bad.add(name + " (延迟累加 " + sumDelay + "t [单条最大 " + maxDelay + "t] vs 超时 " + timeout + "t)");
            }
        }
        assertTrue(checked >= 100, "应扫到 ≥100 条 gametest 用例, 实际 " + checked);
        assertTrue(bad.isEmpty(),
                "以下 gametest 的等待时间 ≥ timeoutTicks ⇒ 必然红 (放宽等待时必须同步抬 timeoutTicks): " + bad);
    }

    @Test
    void sensitiveFamiliesStayInOwnBatches() throws IOException {
        String src = readAllTestSources();
        // 挖矿家族 (走路 + 蓄力 + 掉落) 与 农家/刷子/熔炉寻路家族 — 必须独占批次, 不与 defaultBatch 抢并发
        List<String> oreFamily = List.of("lmaChainOreBuriedSkipped", "lmaChainOreWallForceDigUp",
                "lmaChainOreStackedVein", "lmaChainOreWallDropCollect", "lmaChainOreFar", "lmaChainOreSwapNextVein");
        List<String> slowFamily = List.of("lmaFarmSugarCaneTop", "lmaFarmPlant", "lmaFarmBerryBush",
                "lmaBrush", "lmaBrushComplete", "lmaBrushNothing", "lmaFurnaceBlacklist", "lmaFurnaceNavigate");
        List<String> bad = new ArrayList<>();
        for (var e : List.of(java.util.Map.entry("z_ore", oreFamily), java.util.Map.entry("z_slow", slowFamily))) {
            for (String name : e.getValue()) {
                int i = src.indexOf("public static void " + name + "(");
                if (i < 0) { bad.add(name + " 未找到"); continue; }
                int ann = src.lastIndexOf("@GameTest(", i);
                String head = src.substring(ann, Math.min(ann + 400, i));
                if (!head.contains("batch = \"" + e.getKey() + "\"")) {
                    bad.add(name + " 应属于批次 " + e.getKey());
                }
            }
        }
        assertTrue(bad.isEmpty(), "敏感家族用例的批次隔离被破坏 (会重新引入超时抖动): " + bad);
    }
}
