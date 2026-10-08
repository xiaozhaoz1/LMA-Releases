package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * **菜单 ↔ 屏 配对守护** (v79.63 建立; 2026-09-21 **表驱动改造** —— 用户批准)。
 *
 * <p><b>v79.63 事故</b>: `maid_assembly` 菜单注册了但屏注册依赖 `LmaMenus` 注入时序 ⇒ 注入未完成时
 * `register(null, …)` **静默落在 null 键** ⇒ 用户看到"界面凭空消失"。
 *
 * <p><b>2026-09-21 结构变更</b>: 屏注册由"2 平台 × 8 条手写" ✗ ⇒ **1 张表 (`client/GuiScreenBindings.DEFS`) + 2 个平台循环** ✓
 * (学 TLM `client/init/InitContainerGui` 的"一处集中" ✓ + 复用 `PacketRegistry.DEFS` 模式 ✓)。
 * 本类随之升级为**表感知**断言 (8 条, 用户批准清单):
 * <ol>
 *   <li>{@code GuiScreenBindings.DEFS} 非空 + **id 唯一**；</li>
 *   <li>两个平台 ClientEntry **都消费** {@code GuiScreenBindings.DEFS} (防"表加了、某平台没接")；</li>
 *   <li>两个平台 ClientEntry **无逐个 `<M,S>register` 手写残留** (防回退)；</li>
 *   <li>两个平台 common 入口的**菜单 id 集合彼此相等** (旧基线 10/10 ✓)；</li>
 *   <li><b>绑定表 id ⊆ 菜单 id</b> (每个绑定都必须有对应菜单)；</li>
 *   <li><b>菜单 id − 绑定 id == 白名单</b> (且白名单每项**带原因**, 见 {@link #ALLOWED_UNPAIRED})；</li>
 *   <li>差集报错**区分哪边多** (两侧分别列出)；</li>
 *   <li>两个平台 **common 入口也无屏注册残留** (它们只管 MenuType ✓)。</li>
 * </ol>
 */
class MenuScreenPairingGuardTest {

    /**
     * **已登记"菜单有、绑定表没有"的项 —— 每项必须带原因** (用户要求: 白名单带原因 ✓)。
     * 新增任何一项都会被用例 6 逼着写进这里 ⇒ 无法静默漏掉 ✓。
     */
    private static final Map<String, String> ALLOWED_UNPAIRED = new LinkedHashMap<>() {{
        put("maid_assembly",
                "屏由 **compat 模块自注册** (`compat/create/client/CreateCompatClient`, 按平台分支计 1 条) ⇒ 不进共享表 ✓");
        put("passive_toggle_config",
                "**死对** (2026-09-21 实测: `PassiveToggleConfigScreen` 全仓 0 引用 ✗; 其构造另需第 4 参 taskType, 菜单不携带) "
                        + "⇒ 菜单类型注册但永不可打开; 待裁定删死链或补全 (会话 #310/#311)");
    }};

    private static final Pattern MENU_REG = Pattern.compile("MENU_TYPES\\.register\\(\"([a-z_]+)\"");
    /** 旧手写形态: 显式类型参数 + register ⇒ 已被表驱动取代, 残留即回退 ✗ */
    private static final Pattern HAND_WRITTEN_GENERIC_REG = Pattern.compile("(?:MenuScreens|event)\\.<");
    /** 表驱动循环里允许的**裸**注册调用 */
    private static final Pattern RAW_REG_CALL = Pattern.compile("(?:MenuScreens|event)\\.register\\(");

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static List<String> countMenuTypes(Path file) {
        List<String> out = new ArrayList<>();
        Matcher m = MENU_REG.matcher(ArchSource.read(file));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** 屏注册文件: forge 用 `MenuScreens.register` / neoforge 用 `event.register` ⇒ 两者都数。 */
    private static int countRawRegs(Path file) {
        String s = ArchSource.read(file);
        return count(RAW_REG_CALL, s);
    }

    /** 从绑定表源码里抠 id (表是纯数据 ⇒ 正则足够; 也顺带断言"表里没有裸字符串以外的东西") */
    private static final Pattern BINDING_ID = Pattern.compile("new Binding<[^>]*>\\(\"([a-z_]+)\"");

    private static List<String> bindingIds(String src) {
        List<String> out = new ArrayList<>();
        Matcher m = BINDING_ID.matcher(src);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static Path repoRoot() {
        // findMainRoot() = <repo>/common/src/main/java ⇒ 上 4 层 = 仓库根
        return ArchSource.findMainRoot().getParent().getParent().getParent().getParent();
    }

    private static final String C = "common/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/";
    private static final String F = "forge/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/";
    private static final String N = "neoforge/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/";

    /**
     * **表驱动完整性 + 无残留** (2026-09-21 新增; 8 条断言分列在下方 assert 处)。
     *
     * <p>前身 `everyRegisteredMenuTypeHasAScreenOnBothPlatforms` 的"数菜单个数 vs 数屏注册个数"✗
     * 已被本用例**双向差集**取代 (更硬: 逐 id 对账, 而不是只对数量 ✓)。
     */
    @Test
    void tableDrivenRegistrationIsCompleteAndResidualFree() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");
        Path repo = repoRoot();

        Path bindingsFile = repo.resolve(C + "client/GuiScreenBindings.java");
        Path forgeMenus = repo.resolve(C + "LittleMaidMoreAction.java");
        Path neoMenus = repo.resolve(N + "LmaNeoForgeEntry.java");
        Path forgeClient = repo.resolve(F + "LmaForgeClientEntry.java");
        Path neoClient = repo.resolve(N + "LmaNeoForgeClientEntry.java");
        for (Path p : new Path[]{bindingsFile, forgeMenus, neoMenus, forgeClient, neoClient}) {
            assertTrue(Files.isRegularFile(p), "找不到文件 (路径定位失败): " + p);
        }
        String bindingsSrc = ArchSource.read(bindingsFile);

        // ── 断言 1: 表非空 + id 唯一 ──
        List<String> bindIds = bindingIds(bindingsSrc);
        assertFalse(bindIds.isEmpty(), "GuiScreenBindings.DEFS 为空 — 绑定表必须含全部已配对 GUI");
        assertEquals(bindIds.size(), new TreeSet<>(bindIds).size(),
                "GuiScreenBindings.DEFS 出现**重复 id** (表内自洽性): " + bindIds);

        // ── 断言 2: 两个平台 ClientEntry 都消费绑定表 ──
        for (String rel : new String[]{F + "LmaForgeClientEntry.java", N + "LmaNeoForgeClientEntry.java"}) {
            String src = ArchSource.read(repo.resolve(rel));
            assertTrue(src.contains("GuiScreenBindings.DEFS"), rel + " 未消费 GuiScreenBindings.DEFS (表加了但这平台没接 ✗)");
            assertTrue(src.contains("GuiScreenBindings.screenOf("), rel + " 未通过 screenOf 取屏构造器 (表驱动未接线 ✗)");
        }

        // ── 断言 3 + 8: 无逐个 `<M,S>register` 手写残留 (ClientEntry 与 common 入口都查) ──
        List<String> residual = new ArrayList<>();
        for (String rel : new String[]{F + "LmaForgeClientEntry.java", N + "LmaNeoForgeClientEntry.java",
                C + "LittleMaidMoreAction.java", N + "LmaNeoForgeEntry.java"}) {
            int n = count(HAND_WRITTEN_GENERIC_REG, ArchSource.read(repo.resolve(rel)));
            if (n > 0) residual.add(rel + " 有 " + n + " 处 `<M,S>register` 手写残留 (应改走表驱动循环 ✗)");
        }
        assertTrue(residual.isEmpty(), "手写注册残留:\n  " + String.join("\n  ", residual));

        // ── 断言 4: 两平台菜单 id 集合彼此相等 ──
        TreeSet<String> fMenus = new TreeSet<>(countMenuTypes(forgeMenus));
        TreeSet<String> nMenus = new TreeSet<>(countMenuTypes(neoMenus));
        assertEquals(fMenus, nMenus,
                "两平台菜单 id 集合不一致 (旧基线 10/10) — 只有一边有:\n  仅在 forge: " + diff(fMenus, nMenus)
                        + "\n  仅在 neo: " + diff(nMenus, fMenus));

        // ── 断言 5: 绑定表 id ⊆ 菜单 id ──
        TreeSet<String> onlyInBindings = new TreeSet<>(bindIds);
        onlyInBindings.removeAll(fMenus);
        assertTrue(onlyInBindings.isEmpty(),
                "绑定表出现**没有对应菜单类型**的 id (只在本表): " + onlyInBindings + " ⇒ 删掉或补 MENU_TYPES.register");

        // ── 断言 6 + 7: 菜单 id − 绑定 id == 白名单 (每项带原因); 报错区分哪边多 ──
        TreeSet<String> onlyInMenus = new TreeSet<>(fMenus);
        onlyInMenus.removeAll(bindIds);
        TreeSet<String> expected = new TreeSet<>(ALLOWED_UNPAIRED.keySet());
        assertEquals(expected, onlyInMenus,
                "菜单/绑定差集与白名单不符:\n"
                        + "  ⇒ **只在菜单侧** (有菜单无绑定, 必须进 ALLOWED_UNPAIRED 并写原因): " + diff(onlyInMenus, expected)
                        + "\n  ⇒ **只在白名单** (白名单里的项已消失, 应删掉该条目): " + diff(expected, onlyInMenus));

        // ── 断言 2b: 平台循环真的接线 (文本层面: 循环体各一处裸 register + 按 id 取类型) ──
        for (String rel : new String[]{F + "LmaForgeClientEntry.java", N + "LmaNeoForgeClientEntry.java"}) {
            String src = ArchSource.read(repo.resolve(rel));
            assertTrue(countRawRegs(repo.resolve(rel)) >= 1,
                    rel + " 里没有裸 register 调用 ⇒ 表驱动循环没接线 ✗ (应有 1 处, 位于 for 循环体内)");
            assertTrue(src.contains("cfgMenus.get(b.id())"), rel + " 未按 id 取本平台 MenuType (循环体写歪?)");
            assertTrue(src.contains("GuiScreenBindings.screenOf(b)"), rel + " 未从绑定表取屏构造器 ✗");
        }
    }

    private static String diff(TreeSet<String> a, TreeSet<String> b) {
        TreeSet<String> d = new TreeSet<>(a);
        d.removeAll(b);
        return d.isEmpty() ? "(无)" : d.toString();
    }

    @Test
    void screenRegistrationNeverDependsOnLmaMenusInjection() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");
        Path repo = repoRoot();

        String[] files = {
                F + "LmaForgeClientEntry.java",
                N + "LmaNeoForgeClientEntry.java",
                C + "compat/create/client/CreateCompatClient.java",
        };
        List<String> bad = new ArrayList<>();
        for (String rel : files) {
            Path p = repo.resolve(rel);
            assertTrue(Files.isRegularFile(p), "找不到: " + p);
            String src = ArchSource.read(p);
            // 只查"屏注册调用"里是否出现 LmaMenus. (注册参数里带 LmaMenus = 时序依赖)
            Matcher m = Pattern.compile("(?:MenuScreens|event)\\.[^;]*?register\\(([^;]*)\\);").matcher(src);
            while (m.find()) {
                if (m.group(1).contains("LmaMenus.")) {
                    bad.add(rel + " → " + m.group(1).trim().substring(0, Math.min(60, m.group(1).trim().length())));
                }
            }
        }
        assertTrue(bad.isEmpty(),
                "屏注册不得依赖 LmaMenus 注入时序 (事故: 注入未完成 → register(null) 静默失败): " + bad);
    }

    /**
     * **`LmaMenus` 只许存 supplier, 不许存 `.get()` 的值** (官方注册规范: 持有 holder 而非缓存值)。
     *
     * <p>事故: 原设计在 commonSetup 里 `holder.get()` 缓存成静态值, 客户端屏注册早于注入时读到 null
     * ⇒ `register(null, …)` 静默落在 null 键 ⇒ 用户看到"界面消失"。改为 supplier 后值在使用点解析, 时序无关。
     */
    @Test
    void lmaMenusFieldsAreSuppliersNotCachedValues() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");
        Path repo = repoRoot();
        String src = ArchSource.read(repo.resolve(C + "LmaMenus.java"));
        int supplierFields = count(Pattern.compile("public static Supplier<MenuType<"), src);
        int cachedValueFields = count(Pattern.compile("public static MenuType<"), src);
        assertTrue(supplierFields >= 8, "LmaMenus 字段异常 (Supplier 字段 " + supplierFields + " 个, 期望 ≥8)");
        assertTrue(cachedValueFields == 0,
                "LmaMenus 出现「缓存值」字段 (public static MenuType<…>) — 必须改为 Supplier 并在使用点 .get()");
    }
}
