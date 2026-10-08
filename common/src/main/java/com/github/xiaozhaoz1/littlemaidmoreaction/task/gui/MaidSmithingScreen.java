package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * v79.62.1 锻造界面 — 参考原版锻造台布局 (base 26,48 / addition 44,48 / result 98,48 / 箭头 66,52).
 * <p>v79.62.2 用户裁定: 原版 smithing.png 在 1.21.x 是 256×256 九宫格主题图, blit 即一片灰 —
 * 改元装配任务 (MaidAssemblyScreen) 同款「全部代码绘制」: 圆角面板 + 槽位背景 + 标签, 不依赖任何纹理.
 */
public class MaidSmithingScreen extends AbstractContainerScreen<MaidSmithingMenu> {

    private static final int PANEL_W = 176;
    private static final int SMITH_PANEL_H = 166;   // 上段: 锻造台区 (原版面板高)
    private static final int PANEL_H = 230;         // 总高: +女仆背包+玩家背包
    private static final int BORDER = 0xFF373737, FILL = 0xFFC6C6C6;   // 装配同款面板色
    private static final int SLOT_BORDER = 0xFF373737, SLOT_INNER = 0xFF8B8B8B;

    public MaidSmithingScreen(MaidSmithingMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int l = leftPos, t = topPos;
        // 上段: 锻造台区 (圆角面板, 装配同款)
        drawPanel(g, l, t, PANEL_W, SMITH_PANEL_H);
        // 下段: 女仆背包 + 玩家背包 (面板延续, 中间细分隔)
        drawPanel(g, l, t + SMITH_PANEL_H, PANEL_W, PANEL_H - SMITH_PANEL_H);
        // 锻造槽位背景 (原版布局)
        drawSlotBg(g, l + 26, t + 48);
        drawSlotBg(g, l + 44, t + 48);
        drawSlotBg(g, l + 98, t + 48);
        g.drawString(font, "→", l + 66, t + 52, 0xFF404040, false);
        g.drawString(font, title, l + 8, t + 6, 0xFF404040, false);
        // 女仆背包标签
        g.drawString(font, Component.literal("女仆背包"), l + 8, t + 114, 0xFF404040, false);
        // 玩家背包标签
        g.drawString(font, playerInventoryTitle, l + 8, t + 178, 0xFF404040, false);
        // 槽位背景由 Slot 渲染 (Menu 坐标) — 面板底色保证槽位可见
    }

    /** 圆角面板背景 (对齐装配屏样式) */
    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        int r = 3;
        g.fill(x + r, y, x + w - r, y + h, FILL);
        g.fill(x, y + r, x + w, y + h - r, FILL);
        g.fill(x + r, y, x + w - r, y + r, BORDER);
        g.fill(x + r, y + h - r, x + w - r, y + h, BORDER);
        g.fill(x, y + r, x + r, y + h - r, BORDER);
        g.fill(x + w - r, y + r, x + w, y + h - r, BORDER);
        g.fill(x, y, x + r, y + r, BORDER);
        g.fill(x + w - r, y, x + w, y + r, BORDER);
        g.fill(x, y + h - r, x + r, y + h, BORDER);
        g.fill(x + w - r, y + h - r, x + w, y + h, BORDER);
    }

    /** 槽位背景 (对齐装配屏样式) */
    private static void drawSlotBg(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_BORDER);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_INNER);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    /** 不透明深色背景 (装配同款, 不半透明) */
    @Override
//? if 1.20.1 {
    public void renderBackground(GuiGraphics g) {
//?} else {
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
//?}
        g.fill(0, 0, width, height, 0xFF202020);
    }

    @Override public boolean isPauseScreen() { return false; }
}
