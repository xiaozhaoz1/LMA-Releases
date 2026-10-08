package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI 文案守护（用户裁定 2026-09-18 ✓）—— 界面文案**必须走语言文件** ✓
 *
 * <p><b>事故背景</b>：切英文语言后设置屏仍是中文 ✗ ⇒ 根因不是语言文件 ✗（en_us 本就零中文 ✓），
 * 而是屏幕里用 {@code Component.literal("中文")} **写死** ✗ ⇒ 不查语言文件 ⇒ 切语言也翻不动 ✓。
 * 全仓共 211 处 / 13 文件，已全部转为 {@code Component.translatable("…")} ✓。
 *
 * <p>本守护把规则固化为**可执行**约束 ✓：
 * <ol>
 *   <li>{@code screen/**} 与 {@code task/**} 里**禁止**出现"含中文的字符串字面量出现在 literal( 参数区" ✗
 *       （注释行不算 ✓ —— 允许在说明里写反例 ✓；**三元/拼接写法也覆盖** ✓，这是第一版扫描漏过的口径 ✗）</li>
 *   <li>这些源文件里 {@code translatable("键")} 用到的键，**必须同时存在于 zh_cn.json 与 en_us.json** ✓
 *       （防"只加一边"⇒ 另一语言显示原始键名 ✗）</li>
 *   <li>两个语言文件**禁止重复键** ✓
 *       （Gson 遇重复键直接判整个文件非法 ⇒ 全屏文案失效 ✗；我曾在插入新键时与已存在的键撞车 ✗）</li>
 * </ol>
 *
 * <p>⚠ 若将来确实要新增界面文案 ⇒ 用语言键 ✓（zh_cn 中文 / en_us 英文 ✓），**不要**给本测试开白名单 ✗。
 */
class ScreenLangGuardTest {

    /** 受管目录（相对主源根 ✓）—— 这些目录的内容会直接显示给玩家 ✓ */
    private static final List<String> UI_DIRS = List.of("screen", "task");

    private static final Path LANG_REL = Path.of("common", "src", "main", "resources",
            "assets", "littlemaidmoreaction", "lang");

    @Test
    @DisplayName("screen/ 与 task/ 里禁止写死中文文案（必须走语言键）")
    void noHardcodedChineseInUiSources() {
        Path main = ArchSource.findMainRoot();
        // ★ 必须用**包根**（main/com/github/xiaozhaoz1/littlemaidmoreaction）✓ —— 首版误用主源根 ⇒ 扫到 0 文件 ✗
        Path pkg = ArchSource.findPackageRoot();
        assertTrue(main != null && pkg != null, "定位主源根/包根失败（守护自身失效 ⇒ 必须修）");
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (String dir : UI_DIRS) {
            List<Path> files = ArchSource.javaFiles(pkg.resolve(dir));
            scanned += files.size();
            for (Path f : files) {
                List<String> lines = ArchSource.read(f).lines().toList();
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    String trimmed = line.trim();
                    // 注释/import 不算引用 ✓（允许说明文字里提到反例 ✓）
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
                            || trimmed.startsWith("import ")) {
                        continue;
                    }
                    int at = line.indexOf("literal(");
                    if (at < 0) continue;
                    String tail = line.substring(at + "literal(".length());
                    String[] parts = tail.split(String.valueOf((char) 34), -1);   // 按 " 切分: 奇数下标 = 字面量 ✓
                    for (int k = 1; k < parts.length; k += 2) {
                        if (hasCjk(parts[k])) {
                            offenders.add(ArchSource.rel(main, f) + ":" + (i + 1) + "  → \"" + parts[k] + "\"");
                            break;
                        }
                    }
                }
            }
        }
        assertTrue(scanned > 20, "扫描面异常小（" + scanned + " 个 java 文件）⇒ 守护自身失效, 必须修");
        assertTrue(offenders.isEmpty(),
                "UI 里出现写死的中文字面量 ✗ —— 切英文也不会变 ⇒ 中英混杂 ✓\n"
                        + "正确写法: Component.translatable(\"screen.littlemaidmoreaction.<屏>.<项>\") ✓\n"
                        + "并同时在 zh_cn.json（中文）与 en_us.json（英文）补键 ✓\n违规点:\n  "
                        + String.join("\n  ", offenders));
    }

    @Test
    @DisplayName("UI 用到的语言键必须中英双文件都有（防单边缺失）")
    void uiTranslatableKeysExistInBothLangs() throws IOException {
        Path repo = repoRoot();
        Map<String, String> zh = loadLang(repo, "zh_cn.json");
        Map<String, String> en = loadLang(repo, "en_us.json");
        Path main = ArchSource.findMainRoot();
        Path pkg = ArchSource.findPackageRoot();     // ★ 包根 ✓（同测试①的修正 ✗→✓）
        assertTrue(pkg != null, "定位包根失败（守护自身失效 ⇒ 必须修）");
        List<String> missingZh = new ArrayList<>();
        List<String> missingEn = new ArrayList<>();
        int keys = 0;
        for (String dir : UI_DIRS) {
            for (Path f : ArchSource.javaFiles(pkg.resolve(dir))) {
                String text = ArchSource.read(f);
                int idx = 0;
                while ((idx = text.indexOf("translatable(", idx)) >= 0) {
                    int q1 = text.indexOf('"', idx);
                    int q2 = q1 < 0 ? -1 : text.indexOf('"', q1 + 1);
                    if (q1 < 0 || q2 < 0) break;
                    String key = text.substring(q1 + 1, q2);
                    idx = q2 + 1;
                    // ⚠ 跳过**动态拼接键** ✗（如 "task." + MOD_ID + "." + taskType ✓）——
                    //   判据: 该字面量后面紧跟 `+`(拼接) 或以 `.` 结尾(前缀片段) ⇒ 不是完整键 ✓
                    int after = q2 + 1;
                    while (after < text.length() && Character.isWhitespace(text.charAt(after))) after++;
                    boolean dynamic = key.isEmpty() || key.endsWith(".")
                            || (after < text.length() && text.charAt(after) == '+');
                    if (dynamic) continue;
                    keys++;
                    if (!zh.containsKey(key)) missingZh.add(ArchSource.rel(main, f) + " → " + key);
                    if (!en.containsKey(key)) missingEn.add(ArchSource.rel(main, f) + " → " + key);
                }
            }
        }
        assertTrue(keys > 50, "提取到的语言键异常少（" + keys + "）⇒ 守护自身失效, 必须修");
        assertTrue(missingZh.isEmpty() && missingEn.isEmpty(),
                "UI 引用的语言键在语言文件里缺失 ✗（会显示原始键名 ✗）\n"
                        + "  只在 zh_cn 缺失: " + missingZh + "\n  只在 en_us 缺失: " + missingEn);
    }

    @Test
    @DisplayName("语言文件禁止重复键（Gson 遇重复键判整个文件非法）")
    void langFilesHaveNoDuplicateKeys() throws IOException {
        Path repo = repoRoot();
        for (String name : List.of("zh_cn.json", "en_us.json")) {
            Path p = repo.resolve(LANG_REL).resolve(name);
            List<String> dups = new ArrayList<>();
            Map<String, Integer> seen = new LinkedHashMap<>();
            for (String line : Files.readAllLines(p)) {
                String t = line.trim();
                if (!t.startsWith(String.valueOf((char) 34))) continue;
                int q2 = t.indexOf('"', 1);
                if (q2 < 0) continue;
                String key = t.substring(1, q2);
                int c = seen.merge(key, 1, Integer::sum);
                if (c == 2) dups.add(key);
            }
            assertTrue(dups.isEmpty(), name + " 存在重复键 ✗ ⇒ Gson 解析失败 ⇒ 该语言整个文件失效 ✓\n  重复: " + dups);
        }
    }

    // ── 工具 ──

    /** 仓库根 = 主源根(common/src/main/java) 上跳 3 级 ✓（有 settings.gradle.kts 为证 ✓，不依赖被忽略的 docs/ ✗） */
    private static Path repoRoot() {
        Path p = ArchSource.findMainRoot();
        for (int i = 0; i < 6 && p != null; i++) {
            if (Files.isRegularFile(p.resolve("settings.gradle.kts"))) return p;
            p = p.getParent();
        }
        throw new IllegalStateException("仓库根定位失败（守护自身失效 ⇒ 必须修）");
    }

    private static Map<String, String> loadLang(Path repo, String name) throws IOException {
        Path p = repo.resolve(LANG_REL).resolve(name);
        assertTrue(Files.isRegularFile(p), "语言文件不存在: " + p);
        String json = Files.readString(p);
        Map<String, String> m = new Gson().fromJson(json, new TypeToken<Map<String, String>>() { }.getType());
        assertTrue(m != null && !m.isEmpty(), "语言文件解析为空: " + p);
        return m;
    }

    private static boolean hasCjk(String s) {
        for (char c : s.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }
}
