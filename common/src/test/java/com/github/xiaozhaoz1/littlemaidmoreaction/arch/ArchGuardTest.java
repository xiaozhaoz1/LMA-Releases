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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * 契约允许的 api 例外 (vanilla 层可用; **每次新增都要在此显式登记并注明来源与理由**)。
     *
     * <p>原契约 = 「vanilla → 仅 MC/TLM + api.VanillaConstants」。2026-09-21 接口倒置批次
     * (用户裁定: 木棍标记/绑定的能力接口放 {@code api/input/}, 消费方 {@code vanilla/input/world/StickBindUtil}
     * 经 api 访问点取用) ⇒ 显式放行 {@code api.input.StickBindAccess}。
     * 语义不变式仍然成立: vanilla 只依赖 **极少数、被点名的 api 契约访问点**, 不依赖业务实现层。
     */
    private static final java.util.Set<String> ALLOWED_API = java.util.Set.of(
            "api.VanillaConstants",        // 常量 (原契约)
            "api.input.StickBindAccess"    // 2026-09-21: 木棍标记/绑定 + 任务类型能力访问点 (用户裁定接口放 api/input; 实现在 init/LmaTaskBinding)
    );

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
                if (ALLOWED_API.contains(sub)) continue;          // 契约允许 (白名单显式登记)
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

    @Test
    @DisplayName("api 不得 import adapter.* (2026-09-21 接口倒置后清零, 硬红线)")
    void api_mustNotImportAdapter() {
        assertNoImportOf("api", "adapter.",
                "api 是对外契约层, 禁止 import adapter (TLM 桥接层) — 这是中心→边缘的层倒置。\n"
                        + "正解: 在 api 定义接口/钩子, 由 adapter 或 init 在启动期注入\n"
                        + "(先例: NavigationMemoryProvider ← LmaMemoryModuleRegistry.installNavigationMemory);\n"
                        + "装配类 (如原 LittleMaidMoreActionExtension) 应放 init/, 而不是留在 api/");
    }

    @Test
    @DisplayName("network 不得 import event.* (2026-09-21 StickBindUtil 下沉 vanilla 后清零, 硬红线)")
    void network_mustNotImportEvent() {
        assertNoImportOf("network", "event.",
                "network (网络包层) 禁止 import event.* (游戏事件桥) —— 网络包应按数据/服务端对偶处理,\n"
                        + "不依赖事件桥内部工具。正解: 把通用工具下沉 vanilla (先例: StickBindUtil → vanilla/input/world,\n"
                        + "经 api/input/StickBindAccess 注入配置与任务类型能力)");
    }

    @Test
    @DisplayName("task 不得 import compat.* (2026-09-21 YSM 门面倒置后清零, 硬红线; 注册规格表除外)")
    void task_mustNotImportCompat() {
        Path pkgRoot = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(pkgRoot != null, "未找到 common/src/main/java — 跳过");
        Path task = pkgRoot.resolve("task");
        Assumptions.assumeTrue(Files.isDirectory(task), "无 task 目录 — 跳过");

        List<String> bad = new ArrayList<>();
        for (Path f : ArchSource.javaFiles(task)) {
            String rel = ArchSource.rel(pkgRoot, f);
            // 例外: TaskRegistryManifest = 主动任务注册规格表 (显式登记 compat 任务实现, 用户裁定保留)
            if (rel.endsWith("TaskRegistryManifest.java")) continue;
            for (String imp : ArchSource.imports(ArchSource.read(f))) {
                if (imp.startsWith("com.github.xiaozhaoz1.littlemaidmoreaction.compat.")) {
                    bad.add(rel + " -> " + imp);
                }
            }
        }
        assertTrue(bad.isEmpty(),
                "task (核心层) 禁止 import compat (兼容层) — 一次 import 就把核心绑死在可选 mod 上。\n"
                        + "正解: api 定义门面 + 实现方自注册 (先例: YsmAnimationProvider ← compat.ysm.YsmOutput);\n"
                        + "      或走门控注册表 (TaskRegistryManifest 为已登记例外):\n  "
                        + String.join("\n  ", bad));
    }

    /**
     * **第 7 条 (2026-09-21, 用户批准"③")**: **Mod 主类**不得引用 `net.minecraft.client.*` ✗
     * —— 重演**错题 #168** 的唯一**已实证**现场 ("Attempted to load class net/minecraft/client/gui/screens/Screen
     * for invalid dist DEDICATED_SERVER" ✗; 当时就是主类里的全限定 Screen 引用)。
     *
     * <p><b>为什么只锁主类 (实测收窄 2 轮的结论 ✓)</b>:
     * <ol>
     *   <li>dist-cleaner 是**字节码/解析级** ✗ —— 它拦的是"**在服务端加载 `@OnlyIn(CLIENT)` 的 MC 类**" ✗,
     *       而 **JVM 解析是惰性的** ✓ ⇒ **从未被执行到的客户端分支是安全的** ✓。
     *       实证: `network/SimpleChannelSender`(forge-only, 被主类构造 ✓ 服务端必加载 ✓) 在 `sendToServer`
     *       (只会被客户端调用 ✓) 里调 `Minecraft.getInstance()` ✓ —— 长期存在且 **forge 121×1 全绿** ✓✓
     *       ⇒ 这一类**不是**雷 ✗, 不能用规则一刀切 ✗ (会制造无谓改动 ✗)。</li>
     *   <li>主类不同 ✓: 它的方法在**启动/构造期**就被走 ✓, 引用会被**立即解析** ✗ ⇒ 必炸 ✓ (错题 #168 ✓)。</li>
     *   <li>范围再大一圈需人工逐点判断 ✗ (哪些方法在服务端会执行 ✗ 静态不可判) ⇒ 本规则**只钉主类**，
     *       并把判断依据写在这里 ✓; 注释/字符串**不算** ✓ (不进常量池 ✓ —— 实测 15 处服务端侧 `screen/` 引用
     *       **全是注释** ✓ 从未出事 ✓)。</li>
     * </ol>
     */
    @Test
    @DisplayName("Mod 主类不得引用 net.minecraft.client.* (错题 #168 唯一实证现场, 2026-09-21)")
    void mainClass_mustNotReferenceMcClientClasses() {
        Path pkgRoot = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(pkgRoot != null, "未找到包根 — 跳过");
        Path mainClass = pkgRoot.resolve("LittleMaidMoreAction.java");
        Assumptions.assumeTrue(Files.isRegularFile(mainClass), "未找到主类 — 跳过");

        String code = stripCommentsAndStrings(ArchSource.read(mainClass));
        Matcher m = Pattern.compile("net\\.minecraft\\.client\\.[A-Za-z0-9_.]+").matcher(code);
        List<String> hits = new ArrayList<>();
        while (m.find()) hits.add(m.group());
        assertTrue(hits.isEmpty(),
                "Mod 主类出现 net.minecraft.client.* 的**代码**引用 ✗ —— 服务端启动期解析该引用 ⇒ 必崩\n"
                        + "(错题 #168 原话: Attempted to load class net/minecraft/client/... for invalid dist DEDICATED_SERVER): " + hits
                        + "\n正解: 走注入门面 (先例 api/MaidCodexScreenOpener ← 客户端实现方自注册 ✓); 文档提及用 {@code}/{@link} ✓");
    }

    /** 去注释与字符串字面量 (规则只看**代码引用** ✓; 注释不进常量池 ⇒ 不应误报 ✓) */
    private static String stripCommentsAndStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0, n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {          // 行注释
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {   // 块注释
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i = Math.min(i + 2, n);
            } else if (c == '"') {                                            // 字符串
                i++;
                while (i < n && src.charAt(i) != '"') { if (src.charAt(i) == '\\') i++; i++; }
                i = Math.min(i + 1, n);
            } else if (c == '\'') {                                           // 字符字面量
                i++;
                while (i < n && src.charAt(i) != '\'') { if (src.charAt(i) == '\\') i++; i++; }
                i = Math.min(i + 1, n);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
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
