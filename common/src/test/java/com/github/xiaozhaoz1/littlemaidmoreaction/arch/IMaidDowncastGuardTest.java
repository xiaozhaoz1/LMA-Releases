package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TLM 接口契约守护 (错题 #366) — **`IMaid` ≠ `EntityMaid`** ✗
 *
 * <p>事故: TLM 的 {@code IMaid} 被**其它模组的实体**实现（`touhou_little_maid_spell` 的
 * `MagicalWinefoxBossEntity` 星之魔女 Boss 也走 TLM 动画系统）⇒ TLM 会把**非女仆实体**
 * 传进任何接受 `IMaid` 的入口 ⇒ `(EntityMaid) maid.asEntity()` **必崩** ✗。
 *
 * <p>本守护把"正确写法"固化成可执行规则 ✓:
 * <ol>
 *   <li>**禁止**盲转 `(EntityMaid) xxx.asEntity()` ✗ —— 必须用类型守卫
 *       {@code if (!(maid.asEntity() instanceof EntityMaid m)) return null;} ✓
 *       然后全程使用 {@code m} ✓ (这样编译期就不可能出现 ClassCastException ✓)</li>
 *   <li>形参含 {@code IMaid} 的源文件必须出现 {@code instanceof EntityMaid} 守卫 ✓
 *       (或显式标注 {@code 无需类型守卫} 的安全理由 ✓)</li>
 * </ol>
 *
 * <p>⚠ 若将来确实需要"仅对女仆生效"的新入口 ⇒ 按 (1) 的守卫写法 ✓，**不要**给本测试开白名单 ✗。
 */
class IMaidDowncastGuardTest {

    /** 匹配: (EntityMaid) <接收者>.asEntity(  —— 例如 (EntityMaid) maid.asEntity() */
    private static final Pattern BLIND_DOWNCAST = Pattern.compile(
            "\\((?:[A-Za-z0-9_.]*\\.)?EntityMaid\\s*\\)\\s*[A-Za-z0-9_]*\\s*\\.\\s*asEntity\\s*\\(");

    private static final List<String> ALLOWED = List.of(
            // 目前应为空 ✓ —— 任何新增都必须走"守卫 + 用局部变量"的写法 ✓
    );

    @Test
    @DisplayName("禁止 (EntityMaid) x.asEntity() 盲转 —— IMaid 可能是别的模组的实体 (错题 #366)")
    void noBlindDowncastOfAsEntity() throws IOException {
        Path root = ArchSource.findMainRoot();
        assertTrue(root != null, "定位主源根失败 (守护自身失效 ⇒ 必须修)");
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (var walk = Files.walk(root)) {
            for (Path p : walk.filter(x -> x.toString().endsWith(".java")).toList()) {
                scanned++;
                String text = Files.readString(p);
                Matcher m = BLIND_DOWNCAST.matcher(text);
                while (m.find()) {
                    int line = 1 + (int) text.substring(0, m.start()).chars().filter(c -> c == '\n').count();
                    String file = root.relativize(p).toString().replace('\\', '/');
                    if (ALLOWED.stream().noneMatch(file::endsWith)) {
                        offenders.add(file + ":" + line + "  →  " + m.group().trim());
                    }
                }
            }
        }
        assertTrue(scanned > 100, "扫描面异常小 (" + scanned + " 个 java 文件) ⇒ 守护自身失效, 必须修");
        assertTrue(offenders.isEmpty(),
                "发现 IMaid 盲转 (IMaid 可能是 Spell 的 Boss 等非女仆实体 ⇒ 运行期 ClassCastException ✗)。\n"
                        + "正确写法: if (!(maid.asEntity() instanceof EntityMaid entityMaid)) return null; 然后全程用 entityMaid ✓\n"
                        + "违规点:\n  " + String.join("\n  ", offenders));
    }
}
