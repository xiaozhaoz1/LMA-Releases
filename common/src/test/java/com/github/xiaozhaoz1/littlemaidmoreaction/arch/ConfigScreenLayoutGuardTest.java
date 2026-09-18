package com.github.xiaozhaoz1.littlemaidmoreaction.arch;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置屏**统一布局**守护 (v79.63.8 用户裁定)。
 *
 * <p>规范: 行起点 {@code rowY(row)} (= topPos+40 + row×26) · 控件 x {@code ctrlX()} / 宽 {@code ctrlW()} ·
 * 标签 {@code drawLabel(...)} (左列 contentX) — 面板 256×256 (TLM {@code AbstractMaidContainerGui} 实测) ⇒
 * 控件宽实际 88 ✓。**禁止**再写 {@code contentY()} / {@code topPos + N} / {@code .pos(cx} 这类魔法坐标 ✗
 * (它们导致各屏布局漂移, 且换语言/换面板尺寸时无法统一调整 ✓)。
 */
class ConfigScreenLayoutGuardTest {

    /** 必须遵守规范的屏 (继承 LmaTaskConfigScreen 的具体屏) */
    private static final List<String> SCREENS = List.of(
            "task/gui/AiControlConfigScreen.java",
            "task/gui/BellRingConfigScreen.java",
            "task/gui/BlockInteractConfigScreen.java",
            "task/gui/CraftChainConfigScreen.java",
            "task/gui/DamFillConfigScreen.java",
            "task/gui/ItemListConfigScreen.java",
            "task/gui/PassiveToggleConfigScreen.java",
            "task/gui/VoidExcavationConfigScreen.java");

    @Test
    void layoutUsesSharedHelpersOnly() throws IOException {
        Path root = ArchSource.findPackageRoot();
        Assumptions.assumeTrue(Files.isDirectory(root), "源码树不可见 (打包环境) — 跳过");

        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (String rel : SCREENS) {
            Path f = root.resolve(rel);
            if (!Files.isRegularFile(f)) { bad.add(rel + " 缺失"); continue; }
            String src = Files.readString(f);
            // 取 initAdditionWidgets 方法体 (到下一个方法/文件末)
            int i = src.indexOf("protected void initAdditionWidgets()");
            if (i < 0) { bad.add(rel + " 无 initAdditionWidgets"); continue; }
            int end = src.indexOf("protected void renderAddition", i);
            String body = src.substring(i, end < 0 ? src.length() : end);
            checked++;
            if (!body.contains("rowY(")) bad.add(rel + " 未使用 rowY(...)");
            if (!body.contains("ctrlX") && !body.contains("rowX")) bad.add(rel + " 未使用 ctrlX()/rowX");
            if (!body.contains("ctrlW") && !body.contains("rowW")) bad.add(rel + " 未使用 ctrlW()/rowW");
            if (body.contains("contentY()")) bad.add(rel + " 仍在用 contentY() (魔法起点) ✗");
            if (body.contains("topPos +")) bad.add(rel + " 仍在用 topPos + N (魔法偏移) ✗");
            if (body.contains(".pos(cx")) bad.add(rel + " 仍在用 .pos(cx…) (魔法 x) ✗");
        }
        assertTrue(checked >= SCREENS.size(), "应有 " + SCREENS.size() + " 屏参与守护, 实际 " + checked);
        assertTrue(bad.isEmpty(), "配置屏布局必须走统一助手 (rowY/ctrlX/ctrlW/drawLabel): " + bad);
    }
}
