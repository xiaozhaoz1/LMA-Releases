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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架构越层守护 (v79.63 B2) — 分层契约的 CI 可见红线。
 *
 * <p>契约 (`AI-CODING-GUIDE §1`): {@code vanilla/* → 仅 MC/TLM + api.VanillaConstants}。
 * V1–V6 已把 18 处越层清零, 本测试把它**钉住**: 新增越层立即红, 已知欠账显式登记 (ratchet)。
 *
 * <p><b>2026-09-11 A4 清零</b>: 原白名单 3 处欠账已全部修完 (_DebugSelectionCoordinator_ 改注入判定 ·
 * _MaidAttrRegistry_ 改用类内 NS 常量 · _CropQuery_ 改用 RegionBox 纯数据盒) ⇒ **本测试现为零容忍**。
 * 白名单机制保留 (KNOWN_DEBT 空表): 将来若确需临时例外, 在此显式登记并注明迁移方案, 过期会自动提示删除。
 */
class ArchGuardTest {

    /** 契约允许的唯一 api 例外 (vanilla 层可用常量) */
    private static final String ALLOWED_API = "api.VanillaConstants";

    /**
     * 已知欠账 (A4, 2026-09-11 审计实测) — 键 = 相对包根路径, 值 = 被越层 import。
     * 修好一处请同步删一行 (测试会在白名单过期时给出提示, 但不因此失败)。
     */
    private static final Map<String, String> KNOWN_DEBT = new LinkedHashMap<>();

    static {
    }

    @Test
    @DisplayName("vanilla 不得新增越层 import (契约: 仅 MC/TLM + api.VanillaConstants)")
    void vanilla_noNewLayerViolation() {
        Path pkgRoot = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(pkgRoot != null, "未找到 common/src/main/java — 跳过源码扫描守护");
        Path vanilla = pkgRoot.resolve("vanilla");
        Assumptions.assumeTrue(Files.isDirectory(vanilla), "无 vanilla 目录 — 跳过");

        List<String> unexpected = new ArrayList<>();
        List<String> found = new ArrayList<>();

        for (Path f : ArchSource.javaFiles(vanilla)) {
            String rel = ArchSource.rel(pkgRoot, f);
            for (String imp : ArchSource.imports(ArchSource.read(f))) {
                if (!imp.startsWith("com.github.xiaozhaoz1.littlemaidmoreaction.")) continue;
                String sub = imp.substring("com.github.xiaozhaoz1.littlemaidmoreaction.".length());
                if (sub.equals(ALLOWED_API)) continue;          // 契约允许
                if (sub.startsWith("vanilla.")) continue;        // 本层内部
                // 根包类 (LittleMaidMoreAction = LOGGER/常量宿主) — 全项目通用, 不算业务越层
                if (!sub.contains(".")) continue;
                found.add(rel + " -> " + sub);
            }
        }

        for (String v : found) {
            boolean known = KNOWN_DEBT.entrySet().stream()
                    .anyMatch(e -> v.equals(e.getKey() + " -> " + e.getValue()));
            if (!known) unexpected.add(v);
        }

        assertTrue(unexpected.isEmpty(),
                "vanilla 出现**新**越层 (契约: vanilla → 仅 MC/TLM + api.VanillaConstants)。\n"
                        + "要么把依赖移出 vanilla 层, 要么在 ArchGuardTest.KNOWN_DEBT 显式登记 (需评审):\n  "
                        + String.join("\n  ", unexpected));

        // 白名单过期提示 (不失败): 已修的条目该删, 否则白名单会掩盖未来回归
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> e : KNOWN_DEBT.entrySet()) {
            if (!found.contains(e.getKey() + " -> " + e.getValue())) stale.add(e.getKey() + " -> " + e.getValue());
        }
        if (!stale.isEmpty()) {
            System.out.println("[ArchGuard] 以下已知欠账已不存在 — 请从 KNOWN_DEBT 删除: " + stale);
        }
    }

    @Test
    @DisplayName("vanilla 不得 import task.* (V1–V6 已清零, 硬红线)")
    void vanilla_mustNotImportTask() {
        assertNoImportOf("vanilla", "task.",
                "vanilla 禁止 import task.* (含历史例外 — 已由 V5/V6 清零)");
    }

    @Test
    @DisplayName("storage 不得 import task.* (v79.63 FestivalTable 下沉后清零, 硬红线)")
    void storage_mustNotImportTask() {
        assertNoImportOf("storage", "task.",
                "storage 是底层 (存档 + 静态数据表), 禁止反向 import task.*\n"
                        + "—— 数据表应下沉 storage (如 FestivalTable), 或让调用方注入数据");
    }

    /** 通用: 某层禁止 import 指定前缀 */
    private void assertNoImportOf(String layer, String forbiddenPrefix, String message) {
        Path pkgRoot = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(pkgRoot != null, "未找到 common/src/main/java — 跳过");
        Path layerRoot = pkgRoot.resolve(layer);
        Assumptions.assumeTrue(Files.isDirectory(layerRoot), "无 " + layer + " 目录 — 跳过");

        List<String> bad = new ArrayList<>();
        for (Path f : ArchSource.javaFiles(layerRoot)) {
            for (String imp : ArchSource.imports(ArchSource.read(f))) {
                if (imp.startsWith("com.github.xiaozhaoz1.littlemaidmoreaction." + forbiddenPrefix)) {
                    bad.add(ArchSource.rel(pkgRoot, f) + " -> " + imp);
                }
            }
        }
        assertTrue(bad.isEmpty(), message + ":\n  " + String.join("\n  ", bad));
    }
}
