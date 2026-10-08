package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
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
 * 文档**可抄数字**防漂移守护 (v79.67 — 错题 #353 教训 ①/⑤, 用户 2026-09-17 裁定"加")。
 *
 * <p><b>为什么需要</b>: 项目定「ARCHITECTURE 顶部基线表 = 唯一数字真相源, 其他文档**禁止抄写数字**, 一律指向本表」,
 * 但本轮文档审计实测 **11 处抄来的数字全部腐坏** ✗ (单测类/用例数 · common 文件数 · vanilla 分层数 ·
 * 错题集条数 · "连号"声明 · 版本号), 而 `ReadmeDriftGuardTest` 只查"类名出现过/章节存在", **不查数字** ⇒
 * 盲区必须机械化 (人记不住, 机器能拦) ✓
 *
 * <p><b>本测试守护"当前态文档"</b> (仓库根 `AGENTS.md` / `README.md` · `docs/README.md` ·
 * `docs/AI-CODING-GUIDE.md` · `docs/PROJECT_OVERVIEW.md` · `common/src/&#42;&#42;/README.md`):
 * 禁止出现**可从基线表/文件树抄来的指标数字**:
 * <ol>
 *   <li>单测计数 — `N 类 N 用例`;</li>
 *   <li>文件计数 — `N main` / `N test java|类|文件`;</li>
 *   <li>gametest 计数 — `forge|neoforge … N/N`;</li>
 *   <li>错题集条数 — 含"错题 / lessons"的行里的 `N 条`。</li>
 * </ol>
 *
 * <p><b>允许的指针写法</b> (零副本理念): "见基线表" · "见其头行" · 区间指针 `#150-353` ✓。
 *
 * <p><b>刻意不守护 (写清原因, 免得后来者以为是漏了)</b>:
 * <ul>
 *   <li>**版本号** (`v79.67` / `0.9.67`) — 与"历史特性版本标签" (`v79.62 区域制` / `v79.63.23 迁入`) 无法机械区分,
 *       且面向使用者的头部版本行有导航价值 ⇒ 靠 `AI-CODING-GUIDE` §9 同步清单 + 人工 ✓;</li>
 *   <li>**真相源与历史档** — `docs/ARCHITECTURE.md` (真相源本身) · `changelog.md` · `docs/lessons-learned.md`
 *       (错题集正文大量历史数字) · `docs/history/**` · `docs/plans/**` (当时报告) · `docs/HANDOFF-*.md` (当时交接) 一律排除 ✓。</li>
 * </ul>
 *
 * <p><b>刻意的宽松</b>: 只做"行级模式匹配", 不解析语义 — 目的是**让抄数字变红**, 不是替代判断 (与
 * {@link ReadmeDriftGuardTest} 同哲学) ✓。
 */
class DocsNumberDriftGuardTest {

    /** 受守护的"当前态文档" (相对仓库根; 目录形式的在下面递归展开) */
    private static final List<String> GUARDED_FILES = List.of(
            "AGENTS.md",
            "README.md",
            "docs/README.md",
            "docs/AI-CODING-GUIDE.md",
            "docs/PROJECT_OVERVIEW.md");

    /** 禁止的"可抄数字"模式 (每条对应一类真实腐坏) */
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("\\d+\\s*类\\s*\\d+\\s*用例"),                        // 单测类/用例数 (基线表「单测」行)
            Pattern.compile("\\d+\\s*main\\b"),                                   // 文件计数 (基线表「common 文件」行)
            Pattern.compile("\\d+\\s*test\\s*(?:java|类|文件)"),                   // 同上 (test 侧)
            Pattern.compile("(?:forge|neoforge)[^。\\n]{0,10}?\\d+\\s*/\\s*\\d+"), // gametest 计数 (基线表「gametest」行)
            Pattern.compile("(?:错题|lessons)[^\\n]{0,40}?\\d+\\s*条"));          // 错题集条数 (头行为真相源)

    /**
     * 仓库根 — 从 {@link ArchSource#findMainRoot()} 向上找**含 `docs/ARCHITECTURE.md` 的目录**.
     *
     * <p>⚠️ 教训 (本测试首版翻车): 首版用 `getParent()×3` 手推层数 ⇒ 路径算错 ⇒
     * `Assumptions.assumeTrue` **静默跳过** ⇒ 探针(故意放坏数字)竟然"全绿" ✗
     * ⇒ 现改为**特征定位** + 找不到就**失败而非跳过** (见下方 `assertTrue(repo != null)`) ✓
     */
    private static Path repoRoot() {
        Path p = ArchSource.findMainRoot();          // <repo>/common/src/main/java (打包环境为 null)
        if (p == null) return null;
        for (int i = 0; i < 8 && p != null; i++) {
            if (Files.isRegularFile(p.resolve("docs").resolve("ARCHITECTURE.md"))) return p;
            p = p.getParent();
        }
        return null;
    }

    @Test
    @DisplayName("当前态文档不得抄写基线表指标数字 (单测/文件/gametest/错题条数)")
    void currentStateDocsHaveNoCopiedNumbers() throws IOException {
        Path repo = repoRoot();
        if (ArchSource.findMainRoot() == null) {
            Assumptions.abort("非源码树环境 (打包后跑测) — 跳过");
        }
        // ⚠ 关键: 源码树在但仓库根定位失败 = **本测试自身坏了** ⇒ 必须红, 不许静默跳过 (首版翻车教训)
        assertTrue(repo != null, "仓库根定位失败 (docs/ARCHITECTURE.md 未找到) — 本守护失效, 修 repoRoot() 而非跳过");

        List<Path> targets = new ArrayList<>();
        for (String rel : GUARDED_FILES) {
            Path p = repo.resolve(rel);
            if (Files.isRegularFile(p)) targets.add(p);
        }
        // 包 README: common/src/**/README.md
        try (Stream<Path> s = Files.walk(repo.resolve("common").resolve("src"))) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("README.md"))
                    .forEach(targets::add);
        }

        // ⚠ 自检扫描面: 走歪了必须红 (首版静默跳过 = 假守护教训 ✓)
        assertTrue(targets.size() >= 20,
                "只扫到 " + targets.size() + " 个文档 — 扫描面异常 (期望 ≥20: 根/文档/包 README) ⇒ 守护失效 ✗");

        List<String> bad = new ArrayList<>();
        for (Path p : targets) {
            List<String> lines = Files.readAllLines(p);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (Pattern pat : FORBIDDEN) {
                    Matcher m = pat.matcher(line);
                    if (m.find()) {
                        bad.add(repo.relativize(p) + ":" + (i + 1) + " 「" + m.group() + "」");
                        break;   // 一行只报一次即可
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(),
                "当前态文档出现**可抄数字** ✗ — 数字一律指向 ARCHITECTURE 基线表 (或写 '见其头行'); 详见错题 #353:\n  "
                        + String.join("\n  ", bad));
    }
}
