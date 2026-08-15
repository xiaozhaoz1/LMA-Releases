package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.MaidAttrRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v79.25: 女仆属性屏 — 独立全屏 (非 256×256, 用户裁定 "点选后另开属性屏, 不要 256×256 那么小,
 * 这个是独立屏")。数据: {@link MaidAttrRegistry#getAll()} 16 项, 按 category 分区
 * (工作/战斗/生存/其他), 无 mod 分组/收藏 (用户裁定 "只要按属性区分就好了")。
 * <p><b>v79.26 重设计</b> (用户裁定: "参数界面也扣扣索索, 大方好看一些重新设计"):
 * 面板 420×300 → <b>480×360</b>; 行高 14 → <b>22</b> (纸感行 18 内高 + 4 间距);
 * 组标题 18 → <b>24</b> 金色大字 + 分隔线; 值右对齐金色 — 层次分明不局促。</p>
 * <p><b>v79.26.3 背景全景</b>: renderBackground 换原版旋转全景 (统一 {@link PanoramaBackground},
 * 去 TLM 深棕渐变)。
 * <p><b>v79.26.4 去纸感卷大面板</b> (属性屏就全景, 米黄不好看): 删双层边框
 * + 米黄大填充 — 标题移屏顶, 女仆名/内容直接浮全景; 属性行保留半透明米黄行底 (0x55F2E8D9,
 * 深色字浮全景可读), 组标题加同款半透明底。</p>
 */
public final class MaidAttributeScreen extends Screen {

    /** 大方化: 480×360 (仅作内容区边界, 无面板底) */
    private static final int PANEL_W = 480;
    private static final int PANEL_H = 360;
    private static final int ROW_H = 22;
    private static final int GROUP_TITLE_H = 24;
    /** category 固定顺序 (工作 → 战斗 → 生存 → 其他) — 分组键 = {@link MaidAttrRegistry} category
     *  数据契约 (中文值), 不可改 lang key (computeIfAbsent(e.category()) 分组依赖), 渲染时经 CATEGORY_KEYS 翻译 */
    private static final String[] CATEGORIES = {"工作", "战斗", "生存", "其他"};

    /** category 数据值 → lang key (仅显示层翻译, 分组键保持原值) */
    private static final Map<String, String> CATEGORY_KEYS = Map.of(
            "工作", "gui.littlemaidmoreaction.maid_attr.category.work",
            "战斗", "gui.littlemaidmoreaction.maid_attr.category.combat",
            "生存", "gui.littlemaidmoreaction.maid_attr.category.survival",
            "其他", "gui.littlemaidmoreaction.maid_attr.category.other");

    /** 纸感色板 (MaidAttributeDisplay 画风扩展 — 金色标题层次) */
    private static final int COLOR_BORDER = 0xFF8A7F72;
    private static final int COLOR_ROW_BG = 0x55F2E8D9;
    private static final int COLOR_HOVER = 0x88FFF4DF;
    private static final int COLOR_TEXT = 0x303030;
    private static final int COLOR_VALUE = 0x9C6B1F;
    private static final int COLOR_GROUP = 0x8A5A2B;

    private final Screen parent;
    private final EntityMaid maid;
    private final Map<String, List<MaidAttrRegistry.Entry>> groups = new LinkedHashMap<>();
    private int scroll;

    public MaidAttributeScreen(Screen parent, EntityMaid maid) {
        super(Component.translatable("screen.littlemaidmoreaction.maid_attr"));
        this.parent = parent;
        this.maid = maid;
        for (String cat : CATEGORIES) {
            groups.put(cat, new ArrayList<>());
        }
        for (MaidAttrRegistry.Entry e : MaidAttrRegistry.getAll()) {
            groups.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
        }
    }

    @Override
    protected void init() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                btn -> Minecraft.getInstance().setScreen(parent))
                .pos(px + 20, py + PANEL_H - 34).size(100, 24).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        // 去纸感卷大面板 — 标题屏顶, 内容直接浮全景 (行底半透明可读)
        g.drawCenteredString(font, title, this.width / 2, 20, COLOR_TEXT);
        // 顶部: 女仆名金色 + 分隔线 (半透明 — 浮全景)
        g.drawString(font, maid.getName(), px + 20, py + 10, COLOR_GROUP);
        g.fill(px + 20, py + 26, px + PANEL_W - 20, py + 27, 0x558A7F72);
        drawRows(g, px + 20, py + 36, mx, my);
        super.render(g, mx, my, pt);
    }

    /** 原版主菜单旋转全景背景 (统一 {@link PanoramaBackground}, 去 TLM 深棕渐变)。
     *  不调 super: 1.21 默认 renderBackground 含 renderBlurredBackground 模糊 (明确去模糊)。 */
//? if 1.20.1 {
    @Override
    public void renderBackground(GuiGraphics g) {
        PanoramaBackground.render(g);
    }
//?} else {
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        this.renderPanorama(g, pt);
    }
//?}

    /** 内容区 (面板内 20..PANEL_W-20, 高至返回按钮上) — scroll 偏移 + scissor 裁剪 */
    private void drawRows(GuiGraphics g, int rx, int ry, int mx, int my) {
        int contentW = PANEL_W - 40;
        int contentH = PANEL_H - 56 - 50;
        g.enableScissor(rx, ry, rx + contentW, ry + contentH);
        int y = ry - scroll;
        for (Map.Entry<String, List<MaidAttrRegistry.Entry>> group : groups.entrySet()) {
            if (group.getValue().isEmpty()) {
                continue;
            }
            if (y + GROUP_TITLE_H > ry) {
                // 组标题大字 + 装饰短横线; 半透明米黄底 (浮全景可读) — 分组键翻译后显示, 横线宽度随翻译文本
                Component groupTitle = Component.translatable(
                        CATEGORY_KEYS.getOrDefault(group.getKey(), group.getKey()));
                g.fill(rx, y, rx + contentW, y + GROUP_TITLE_H, COLOR_ROW_BG);
                g.drawString(font, groupTitle, rx + 4, y + 4, COLOR_GROUP);
                g.fill(rx + 4, y + GROUP_TITLE_H - 5, rx + 4 + font.width(groupTitle), y + GROUP_TITLE_H - 4, 0xFFD9B380);
            }
            y += GROUP_TITLE_H;
            for (MaidAttrRegistry.Entry e : group.getValue()) {
                if (y + ROW_H >= ry && y <= ry + contentH) {
                    drawRow(g, rx, y, contentW, e, mx, my);
                }
                y += ROW_H;
            }
        }
        g.disableScissor();
    }

    private void drawRow(GuiGraphics g, int rx, int y, int w, MaidAttrRegistry.Entry e, int mx, int my) {
        boolean hovered = mx >= rx && mx < rx + w && my >= y && my < y + ROW_H;
        // 边框 + 纸感背景 (hover 提亮) — 内高 18 上下留白 2
        g.fill(rx, y, rx + w, y + ROW_H, COLOR_BORDER);
        g.fill(rx + 1, y + 1, rx + w - 1, y + ROW_H - 1, hovered ? COLOR_HOVER : COLOR_ROW_BG);
        g.drawString(font, e.display(), rx + 6, y + 4, COLOR_TEXT);
        double value = MaidAttrRegistry.get(maid, e.key());
        String text = String.format(Locale.ROOT, "%.1f", value);
        g.drawString(font, text, rx + w - 6 - font.width(text), y + 4, COLOR_VALUE);
    }

//? if 1.20.1 {
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = clampScroll(scroll + (delta > 0 ? -16 : 16));
        return true;
    }
//?} else {
    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double delta) {
        scroll = clampScroll(scroll + (delta > 0 ? -16 : 16));
        return true;
    }
//?}

    private int clampScroll(int value) {
        int contentH = PANEL_H - 56 - 50;
        int y = 0;
        for (List<MaidAttrRegistry.Entry> list : groups.values()) {
            if (!list.isEmpty()) {
                y += GROUP_TITLE_H + list.size() * ROW_H;
            }
        }
        return Math.max(0, Math.min(Math.max(0, y - contentH), value));
    }
}
