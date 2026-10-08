package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三方类加载守卫 (错题 #368) — **门控拦得住"执行"，拦不住"类加载"** ✗
 *
 * <p>事故: 未装 Create 时 `CreateCompatClient.onRegisterScreens`（`@EventBusSubscriber` ⇒ **无条件注册** ✗）
 * 执行到 `event.register(assemblyMenu, MaidAssemblyScreen::new)` ✗ ⇒ 解析 `MaidAssemblyScreen`
 * ⇒ 连带加载它的**父类** `com.simibubi.create…AbstractSimiContainerScreen` ✗ ⇒ `NoClassDefFoundError` ✓
 * （原有门控 `assemblyMenu != null` 是**假的物理前提** ✗：菜单类型是无条件注册的 ⇒ 未装 Create 时也非 null ✗）
 *
 * <p>本守护固化的规则 ✓：
 * <ol>
 *   <li>先算出"**父类是第三方类**的自有类"集合 ✓（这些类一被解析就会拉着第三方类一起加载 ✗）</li>
 *   <li>凡带 `@EventBusSubscriber`（= 无条件被注册 ✗）的文件，若引用了上述类，
 *       则该引用**必须出现在 `isLoaded(` 物理门控之后** ✓（按行号判定 ✓）</li>
 * </ol>
 *
 * <p>⚠ 若将来需要在本类场景里新增引用 ⇒ 请把 `isLoaded("<modid>")` 门控写到该引用**之前** ✓，
 * **不要**给本测试开白名单 ✗。
 */
class ThirdPartyDerivedGateTest {

    /** 第三方 mod 的包前缀（引用它们的类都需要物理门控 ✓） */
    private static final List<String> THIRD_PARTY_PREFIXES = List.of(
            "com.simibubi.", "net.createmod.", "com.snownee.", "dev.engine_room.", "com.jozufozu.");

    /** 匹配: class X ... extends Foo   /   record X ... implements Foo */
    private static final Pattern DECL = Pattern.compile(
            "\\b(?:class|record)\\s+([A-Za-z0-9_]+)[^{;]*?\\b(?:extends|implements)\\s+([A-Za-z0-9_]+)");

    @Test
    @DisplayName("带 @EventBusSubscriber 的文件引用'第三方派生类'时，必须先过 isLoaded 物理门控 (错题 #368)")
    void eventSubscriberMustGateThirdPartyDerivedClasses() {
        Path main = ArchSource.findMainRoot();
        assertTrue(main != null, "定位主源根失败 (守护自身失效 ⇒ 必须修)");

        List<Path> files = ArchSource.javaFiles(main);
        assertTrue(files.size() > 100, "扫描面异常小 (" + files.size() + " 文件) ⇒ 守护自身失效, 必须修");

        // ── 1) 收集"父类/接口来自第三方包"的自有类 ──
        Map<String, String> thirdPartyDerived = new LinkedHashMap<>();   // 简单名 -> 证据
        for (Path f : files) {
            String text = ArchSource.read(f);
            Matcher m = DECL.matcher(text);
            while (m.find()) {
                String simpleName = m.group(1);          // 自有类名
                String superSimple = m.group(2);         // 其父类/接口的简单名
                if (importsThirdParty(text, superSimple)) {
                    thirdPartyDerived.putIfAbsent(simpleName,
                            ArchSource.rel(main, f) + "  extends/implements " + superSimple);
                }
            }
        }

        // ── 2) @EventBusSubscriber 文件里引用它们 ⇒ **每一处引用**都必须在 isLoaded( 门控之后 ──
        //   ⚠ 首版只比较"首次出现" ✗ ⇒ 会漏检（如 forge 分支门控在前、neo 分支门控被删 ⇒ 仍判绿 ✗）
        //     ⇒ 现按"**同一方法体内、引用之前必须有 isLoaded**"逐处判定 ✓（已用探针复核 ✓）
        List<String> offenders = new ArrayList<>();
        for (Path f : files) {
            String text = ArchSource.read(f);
            if (!text.contains("@EventBusSubscriber")) continue;
            List<String> lines = text.lines().toList();
            for (Map.Entry<String, String> e : thirdPartyDerived.entrySet()) {
                for (int refLine : allRefLines(lines, e.getKey())) {
                    if (!hasGateBeforeInSameMethod(lines, refLine)) {
                        offenders.add(ArchSource.rel(main, f) + ":" + refLine + " 引用了第三方派生类 `" + e.getKey()
                                + "`（" + e.getValue() + "）但**同方法内、引用之前**没有 isLoaded( 门控 ✗");
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "违反'第三方类加载'契约 (错题 #368): @EventBusSubscriber 类会被无条件注册 ✗，"
                        + "它引用的类若其父类来自第三方 mod，解析时会连带加载第三方类 ⇒ 未装该 mod 时 NoClassDefFoundError ✓\n"
                        + "正确写法: 在该引用之前加 `if (!ModList.get().isLoaded(\"<modid>\")) return;` ✓\n违规点:\n  "
                        + String.join("\n  ", offenders));
    }

    /** text 里是否有来自第三方包的 import（形如 import com.simibubi.….<simple>;） */
    private static boolean importsThirdParty(String text, String simple) {
        for (String p : THIRD_PARTY_PREFIXES) {
            Matcher m = Pattern.compile("(?m)^\\s*import\\s+" + Pattern.quote(p).replace("\\.", "\\.") + "\\S*\\."
                    + Pattern.quote(simple) + "\\s*;").matcher(text);
            if (m.find()) return true;
        }
        return false;
    }

    private static int firstLineContaining(String text, String needle) {
        List<String> lines = text.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            if (s.contains(needle) && !s.trim().startsWith("//") && !s.trim().startsWith("*")) return i + 1;
        }
        return -1;
    }

    /** 所有"非注释、非 import"的引用行号 (1-based) —— 每一处都要单独过门控 ✓ */
    private static List<Integer> allRefLines(List<String> lines, String word) {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(word) + "\\b");
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            String t = s.trim();
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) continue;   // 注释不算 ✓
            if (t.startsWith("import ")) continue;                                        // import 编译期, 不加载 ✓
            if (p.matcher(s).find()) out.add(i + 1);
        }
        return out;
    }

    /**
     * 判定：`refLine` 之前、**同一方法体内**是否存在 `isLoaded(` 门控 ✓
     * （先向上找到方法签名/@SubscribeEvent，再在 [方法头, 引用) 区间里找门控 ✓ —— 不用花括号配对，简单且够用 ✓）
     */
    private static boolean hasGateBeforeInSameMethod(List<String> lines, int refLine) {
        int methodStart = -1;
        for (int i = refLine - 2; i >= 0; i--) {
            String t = lines.get(i).trim();
            if (t.contains("@SubscribeEvent")) { methodStart = i; break; }
            if ((t.startsWith("public ") || t.startsWith("private ") || t.startsWith("protected ") || t.startsWith("static "))
                    && t.endsWith("{")) {
                methodStart = i;
                break;
            }
        }
        if (methodStart < 0) return false;      // 找不到方法边界 ⇒ 保守判失败 ✓ (fail-closed ✓)
        for (int i = methodStart; i < refLine - 1; i++) {
            String t = lines.get(i).trim();
            if (t.contains("isLoaded(") && !t.startsWith("//") && !t.startsWith("*")) return true;
        }
        return false;
    }
}
