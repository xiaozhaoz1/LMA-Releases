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
 * v79.25: 女仆属性屏 — 独立全屏, 数据 {@link MaidAttrRegistry#getAll()} 16 项按 category
 * 分区 (工作/战斗/生存/其他), 全景背景 + 滚动内容区。
 *
 * <p><b>v79.61x 重写 (用户裁定: "字体和渲染太难看了重新写")</b> — 暗色玻璃风:
 * ① 字号分级放大 (MC 位图字体无多字号 API, pose.scale 缩放绘制 — 标题 2x /
 * 组标题与女仆名 1.5x / 属性行 1.25x); ② 色板去米黄纸感 — 半透明黑行底
 * (0x80000000, hover 提亮) + 白字 + 金色值/组标题, 浮全景对比度优先;
 * ③ 底部双按钮 — 返回 + 「LMA 参数」 (TLM 任务配置屏: 1.20.1 经
 * TLM NetworkHandler.CHANNEL 发 OpenMaidGuiMessage / 1.21.1 经
 * PacketDistributor 发 OpenMaidGuiPackage, 服务端 openMaidGui(TASK_CONFIG)
 * → TaskConfigGuiFactory.of(maid) 当前任务配置链)。
 */
public final class MaidAttributeScreen extends Screen {

    /** 内容区 480×400 */
    private static final int PANEL_W = 480;
    private static final int PANEL_H = 400;
    /** 行高 (1.25x 字号) — 组标题 36 / 属性行 28 */
    private static final int ROW_H = 28;
    private static final int GROUP_TITLE_H = 36;
    /** 字号分级 — pose.scale 缩放系数 */
    private static final float TITLE_SCALE = 2.0F;
    private static final float GROUP_SCALE = 1.5F;
    private static final float ROW_SCALE = 1.25F;
    /** category 固定顺序 (工作 → 战斗 → 生存 → 其他) — 分组键 = {@link MaidAttrRegistry} category
     *  数据契约 (中文值), 不可改 lang key (computeIfAbsent(e.category()) 分组依赖), 渲染时经 CATEGORY_KEYS 翻译 */
    private static final String[] CATEGORIES = {"工作", "战斗", "生存", "其他"};

    /** category 数据值 → lang key (仅显示层翻译, 分组键保持原值) */
    private static final Map<String, String> CATEGORY_KEYS = Map.of(
            "工作", "gui.littlemaidmoreaction.maid_attr.category.work",
            "战斗", "gui.littlemaidmoreaction.maid_attr.category.combat",
            "生存", "gui.littlemaidmoreaction.maid_attr.category.survival",
            "其他", "gui.littlemaidmoreaction.maid_attr.category.other");

    /** 暗色玻璃风色板 (v79.61x 重写 — 去米黄纸感) */
    private static final int COLOR_ROW_BG = 0x80000000;
    private static final int COLOR_HOVER = 0xA0303030;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_VALUE = 0xFFFFC86B;
    private static final int COLOR_GROUP = 0xFFFFC86B;
    private static final int COLOR_LINE = 0x55FFFFFF;

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
        int by = py + PANEL_H - 34;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                btn -> Minecraft.getInstance().setScreen(parent))
                .pos(px + 20, by).size(100, 24).build());
        // v79.61x: 底部「LMA 参数」→ TLM 任务配置屏 (当前任务配置链)
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.littlemaidmoreaction.maid_attr.lma_params"),
                btn -> openLmaParams())
                .pos(px + PANEL_W - 140, by).size(120, 24).build());
    }

    /** 打开 LMA 参数 (当前任务配置屏) — 双平台 TLM 网络通道 */
    private void openLmaParams() {
        if (maid == null) return;
//? if 1.20.1 {
        com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler.CHANNEL.sendToServer(
                new com.github.tartaricacid.touhoulittlemaid.network.message.OpenMaidGuiMessage(
                        maid.getId(), com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex.TASK_CONFIG));
//?} else {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.github.tartaricacid.touhoulittlemaid.network.message.OpenMaidGuiPackage(
                        maid.getId(), com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex.TASK_CONFIG));
//?}
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
        // 屏标题 2x 白 — 屏顶居中
        drawScaledAny(g, title, TITLE_SCALE,
                this.width / 2f - font.width(title) * TITLE_SCALE / 2f, 16, COLOR_TEXT);
        // 女仆名 1.5x 金 + 细分隔线
        drawScaledAny(g, maid.getName(), GROUP_SCALE, px + 20, py + 6, COLOR_GROUP);
        g.fill(px + 20, py + 34, px + PANEL_W - 20, py + 35, COLOR_LINE);
        drawRows(g, px + 20, py + 40, mx, my);
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

    /** 内容区 (面板内 20..PANEL_W-20, 高至按钮上) — scroll 偏移 + scissor 裁剪 */
    private void drawRows(GuiGraphics g, int rx, int ry, int mx, int my) {
        int contentW = PANEL_W - 40;
        int contentH = PANEL_H - 40 - 60;
        g.enableScissor(rx, ry, rx + contentW, ry + contentH);
        int y = ry - scroll;
        for (Map.Entry<String, List<MaidAttrRegistry.Entry>> group : groups.entrySet()) {
            if (group.getValue().isEmpty()) {
                continue;
            }
            if (y + GROUP_TITLE_H > ry) {
                // 组标题 1.5x 金 + 装饰细线; 半透明黑底 (浮全景可读) — 分组键翻译后显示
                Component groupTitle = Component.translatable(
                        CATEGORY_KEYS.getOrDefault(group.getKey(), group.getKey()));
                g.fill(rx, y, rx + contentW, y + GROUP_TITLE_H, COLOR_ROW_BG);
                drawScaledAny(g, groupTitle, GROUP_SCALE, rx + 6, y + 6, COLOR_GROUP);
                g.fill(rx + 6, y + GROUP_TITLE_H - 6,
                        rx + 6 + scaledWidth(groupTitle, GROUP_SCALE), y + GROUP_TITLE_H - 5, COLOR_LINE);
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
        // 玻璃行底 (hover 提亮) — 内高 26 上下留白 1
        g.fill(rx, y, rx + w, y + ROW_H, hovered ? COLOR_HOVER : COLOR_ROW_BG);
        drawScaledAny(g, e.display(), ROW_SCALE, rx + 8, y + 5, COLOR_TEXT);
        double value = MaidAttrRegistry.get(maid, e.key());
        String text = String.format(Locale.ROOT, "%.1f", value);
        drawScaledAny(g, text, ROW_SCALE,
                rx + w - 8 - scaledWidth(text, ROW_SCALE), y + 5, COLOR_VALUE);
    }

    /** 缩放绘制 (pose.scale — MC 位图字体无多字号 API; Component/String 分派:
     *  EntityMaid.getName 返回 Component, MaidAttrRegistry.Entry.display 返回 String) */
    private void drawScaledAny(GuiGraphics g, Object text, float scale, float x, float y, int color) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);
        if (text instanceof Component c) {
            g.drawString(font, c, (int) (x / scale), (int) (y / scale), color);
        } else {
            g.drawString(font, (String) text, (int) (x / scale), (int) (y / scale), color);
        }
        g.pose().popPose();
    }

    /** 缩放后文本宽度 (右对齐计算; Component/String 分派) */
    private int scaledWidth(Object text, float scale) {
        int w = text instanceof Component c ? font.width(c) : font.width((String) text);
        return (int) (w * scale);
    }

//? if 1.20.1 {
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = clampScroll(scroll + (delta > 0 ? -ROW_H : ROW_H));
        return true;
    }
//?} else {
    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double delta) {
        scroll = clampScroll(scroll + (delta > 0 ? -ROW_H : ROW_H));
        return true;
    }
//?}

    private int clampScroll(int value) {
        int contentH = PANEL_H - 40 - 60;
        int y = 0;
        for (List<MaidAttrRegistry.Entry> list : groups.values()) {
            if (!list.isEmpty()) {
                y += GROUP_TITLE_H + list.size() * ROW_H;
            }
        }
        return Math.max(0, Math.min(Math.max(0, y - contentH), value));
    }
}
