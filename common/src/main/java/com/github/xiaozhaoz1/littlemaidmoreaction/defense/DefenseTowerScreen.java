package com.github.xiaozhaoz1.littlemaidmoreaction.defense;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 防御塔 GUI 屏 (v79.62.3) — 两框分离 (装配界面同款):
 * 框① 主面板: 标题 + 弹药 3×3 + 控制行 (模式/半径/伤害: 文本左 + 按钮右 同行对齐)
 * 框② 物品栏: 3 行 + 快捷栏 4 排全框.
 *
 * <p>按钮走 vanilla {@code handleInventoryButtonClick} → {@link DefenseTowerMenu#clickMenuButton};
 * 值经 DataSlot 广播同步. 不覆写 renderBackground (super.render 内部多态调用会盖面板).
 */
public class DefenseTowerScreen extends AbstractContainerScreen<DefenseTowerMenu> {

    private static final int BORDER = 0xFF404040, FILL = 0xFFD0D0D0;
    private static final int SLOT_BORDER = 0xFF404040, SLOT_INNER = 0xFF9A9A9A;
    private static final int TEXT = 0xFF202020;

    /** 主面板: 标题(6) + 武器栏(标签20+格34) + 弹药栏(标签52+3×3 到106) + 半径(124) + 余量 */
    private static final int PANEL_H = 140;
    private static final int PANEL_W = 176;
    /** 物品栏框高 (对齐装配屏 92: 标签 0..9 + 槽位 9..85 + 底余量) */
    private static final int INV_H = 92;
    /** 半径行 y */
    private static final int CTRL_Y = 122;
    /** 按钮 x (文本右侧) */
    private static final int BTN_X = 104;

    public DefenseTowerScreen(DefenseTowerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H + 4 + INV_H;
        // v79.63: 屏构造完成留痕 (调试"界面打不开": 有这行=屏已构造, 空白则看渲染栈)
        com.github.xiaozhaoz1.littlemaidmoreaction.screen.ScreenLifecycleLog.compatConstructed("defense_tower", true);
    }

    @Override
    protected void init() {
        super.init();
        // 半径 - / + (攻击方式由武器自动判定, 无模式按钮)
        addRenderableWidget(Button.builder(Component.literal("-"),
                b -> click(DefenseTowerMenu.BTN_RADIUS_DOWN))
                .bounds(leftPos + BTN_X, topPos + CTRL_Y, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
                b -> click(DefenseTowerMenu.BTN_RADIUS_UP))
                .bounds(leftPos + BTN_X + 32, topPos + CTRL_Y, 28, 18).build());
    }

    private void click(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int l = leftPos, t = topPos;
        // ── 框① 主面板: 标题 → 武器栏 → 弹药栏 → 半径 ──
        drawPanel(g, l, t, PANEL_W, PANEL_H);
        g.drawString(font, title, l + 8, t + 6, TEXT, false);
        // 武器栏 (1 格)
        g.drawString(font, Component.translatable("gui.littlemaidmoreaction.defense_tower.weapon"),
                l + 8, t + 20, TEXT, false);
        drawSlotBg(g, l + 62, t + 34);   // 武器格 (x62,y34)
        // 弹药栏 (3×3, 9 格) — y 对齐 Menu (52 起)
        g.drawString(font, Component.translatable("gui.littlemaidmoreaction.defense_tower.ammo"),
                l + 8, t + 46, TEXT, false);
        for (int i = 0; i < DefenseTowerMenu.AMMO_SLOTS; i++) {
            drawSlotBg(g, l + 62 + (i % 3) * 18, t + 52 + (i / 3) * 18);
        }
        // 半径: 文本 + -/+ 按钮
        int cy = CTRL_Y;
        g.drawString(font, Component.translatable("gui.littlemaidmoreaction.defense_tower.radius",
                        menu.getRadius()), l + 8, t + cy + 5, TEXT, false);

        // ── 框② 物品栏 (标签顶部, 槽位 invTop+9 — 装配界面同款不偏) ──
        int invTop = t + PANEL_H + 4;
        drawPanel(g, l, invTop, PANEL_W, INV_H);
        g.drawString(font, playerInventoryTitle, l + 8, invTop, TEXT, false);
        int py = invTop + 9;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlotBg(g, l + 8 + col * 18, py + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlotBg(g, l + 8 + col * 18, py + 58);
    }

    /** 圆角面板 (装配同款) */
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

    /** 槽位背景 */
    private static void drawSlotBg(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_BORDER);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_INNER);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // 默认半透明背景 (不覆写 renderBackground — super.render 内部多态调用会盖面板)
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override public boolean isPauseScreen() { return false; }
}
