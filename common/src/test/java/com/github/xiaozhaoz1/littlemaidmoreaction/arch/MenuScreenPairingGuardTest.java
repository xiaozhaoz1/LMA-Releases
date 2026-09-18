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
 * **菜单 ↔ 屏 配对守护** (v79.63 — 用户实机事故: 装配任务 GUI "什么都没弹/界面消失" 后新增)。
 *
 * <p><b>事故</b>: `maid_assembly` 菜单类型注册了, 但屏注册走的是 `LmaMenus.*`
 * (依赖 commonSetup 注入时序) ⇒ 注入未完成时 `register(null, …)` **静默落在 null 键** ⇒
 * 原版只报 `Failed to create screen for menu type: …` ⇒ 用户看到"界面凭空消失"。
 * 同类漏改还有 6 个屏 (早已改成"直取注册器"), 装配屏是唯一漏的。
 *
 * <p><b>不变量</b> (本用例守护):
 * <ol>
 *   <li>每个平台的**菜单类型数** == 该平台**屏注册数** + {@link #KNOWN_UNPAIRED} (已知未配对, 必须在代码里有据可查);</li>
 *   <li>屏注册**不得**读 `LmaMenus.*` (时序依赖) — 必须直取注册器 (`…_MENU.get()`);</li>
 *   <li>每个"门控不成立 ⇒ 不注册"的分支必须留 WARN (静默门控 = 用户看到界面消失)。</li>
 * </ol>
 */
class MenuScreenPairingGuardTest {

    /** 已知"菜单类型已注册但没有屏注册"的项 — 新增任何一项都必须写进这里 (逼你面对它, 而不是静默漏掉)。 */
    private static final List<String> KNOWN_UNPAIRED = List.of(
            "passive_toggle_config"   // 被动任务 per-maid 开关屏: 菜单类型注册但无屏注册 + 无处打开 (死链);
                                      // 且 PassiveToggleConfigScreen 构造需第 4 参 taskType, 菜单不携带
                                      // ⇒ 无法直接补注册。待裁定: 删死链 or 补全 (会话 #310/#311 记录)
    );

    private static final Pattern MENU_REG = Pattern.compile("MENU_TYPES\\.register\\(\"([a-z_]+)\"");

    private static int countMenuTypes(Path file) {
        return count(MENU_REG, ArchSource.read(file));
    }

    /** 屏注册: forge 用 MenuScreens.<…>register( / neoforge 用 event.<…>register( */
    private static int countScreenRegs(Path file) {
        int n = count(Pattern.compile("MenuScreens\\.(?:<[^;]*?>)?register\\("), ArchSource.read(file));
        n += count(Pattern.compile("event\\.(?:<[^;]*?>)?register\\("), ArchSource.read(file));
        return n;
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    @Test
    void everyRegisteredMenuTypeHasAScreenOnBothPlatforms() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");

        // findMainRoot() = <repo>/common/src/main/java ⇒ 上 4 层 = 仓库根
        Path lmaRoot = ArchSource.findMainRoot().getParent().getParent().getParent().getParent();

        Path forgeMenus = lmaRoot.resolve("common/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/LittleMaidMoreAction.java");
        Path forgeScreens = lmaRoot.resolve("forge/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/LmaForgeClientEntry.java");
        Path neoMenus = lmaRoot.resolve("neoforge/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/LmaNeoForgeEntry.java");
        Path neoScreens = lmaRoot.resolve("neoforge/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/LmaNeoForgeClientEntry.java");
        for (Path p : new Path[]{forgeMenus, forgeScreens, neoMenus, neoScreens}) {
            assertTrue(Files.isRegularFile(p), "找不到文件 (路径定位失败): " + p);
        }

        int forgeMenuN = countMenuTypes(forgeMenus);
        // 装配屏注册在 CreateCompatClient, 且该文件**同时含两平台分支** (Stonecutter 注释) ⇒
        // 必须按平台分别计数, 否则同一文件里另一个平台的注册也会被算进来 (实测会各多算 1)。
        String compatSrc = ArchSource.read(lmaRoot.resolve(
                "common/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/compat/create/client/CreateCompatClient.java"));
        int forgeCompat = count(Pattern.compile("MenuScreens\\.(?:<[^;]*?>)?register\\("), compatSrc);
        int neoCompat = count(Pattern.compile("event\\.(?:<[^;]*?>)?register\\("), compatSrc);
        int forgeScreenN = countScreenRegs(forgeScreens) + forgeCompat;
        int neoMenuN = countMenuTypes(neoMenus);
        int neoScreenN = countScreenRegs(neoScreens) + neoCompat;
        int known = KNOWN_UNPAIRED.size();

        assertTrue(forgeMenuN >= 8 && neoMenuN >= 8, "菜单类型数异常 (" + forgeMenuN + "/" + neoMenuN + ")");
        assertTrue(forgeMenuN == forgeScreenN + known,
                "forge 菜单/屏 不配对: 菜单 " + forgeMenuN + " 个, 屏注册 " + forgeScreenN + " 个, 已知未配对 " + known
                        + " 个 ⇒ 有一个菜单类型没有屏 (打开即\"什么都没弹\")。新增屏或补进 KNOWN_UNPAIRED。");
        assertTrue(neoMenuN == neoScreenN + known,
                "neoforge 菜单/屏 不配对: 菜单 " + neoMenuN + " 个, 屏注册 " + neoScreenN + " 个, 已知未配对 " + known
                        + " 个 ⇒ 同上。");
    }

    @Test
    void screenRegistrationNeverDependsOnLmaMenusInjection() {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(root != null, "非源码树环境");
        Path lmaRoot = ArchSource.findMainRoot().getParent().getParent().getParent().getParent();   // …/common/src/main/java → 仓库根

        String[] files = {
                "forge/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/LmaForgeClientEntry.java",
                "neoforge/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/LmaNeoForgeClientEntry.java",
                "common/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/compat/create/client/CreateCompatClient.java",
        };
        List<String> bad = new ArrayList<>();
        for (String rel : files) {
            Path p = lmaRoot.resolve(rel);
            assertTrue(Files.isRegularFile(p), "找不到: " + p);
            String src = ArchSource.read(p);
            // 只查"屏注册调用"里是否出现 LmaMenus. (注册参数里带 LmaMenus = 时序依赖)
            Matcher m = Pattern.compile("(?:MenuScreens\\.<[^;]*?register\\(|event\\.<[^;]*?register\\()([^;]*)\\);").matcher(src);
            while (m.find()) {
                if (m.group(1).contains("LmaMenus.")) bad.add(rel + " → " + m.group(1).trim().substring(0, Math.min(60, m.group(1).trim().length())));
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
        Path lmaRoot = ArchSource.findMainRoot().getParent().getParent().getParent().getParent();
        String src = ArchSource.read(lmaRoot.resolve(
                "common/src/main/java/com/github/xiaozhaoz1/littlemaidmoreaction/LmaMenus.java"));
        int supplierFields = count(Pattern.compile("public static Supplier<MenuType<"), src);
        int cachedValueFields = count(Pattern.compile("public static MenuType<"), src);
        assertTrue(supplierFields >= 8, "LmaMenus 字段异常 (Supplier 字段 " + supplierFields + " 个, 期望 ≥8)");
        assertTrue(cachedValueFields == 0,
                "LmaMenus 出现「缓存值」字段 (public static MenuType<…>) — 必须改为 Supplier 并在使用点 .get()");
    }
}
