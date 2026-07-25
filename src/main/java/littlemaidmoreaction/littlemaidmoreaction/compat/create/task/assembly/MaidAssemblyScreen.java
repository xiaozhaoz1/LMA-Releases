package littlemaidmoreaction.littlemaidmoreaction.compat.create.task.assembly;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Collections;
import java.util.List;

/**
 * 便携装配 Screen — 全部代码绘制。锁定按钮通过 clickMenuButton 发包到服务端。
 */
public final class MaidAssemblyScreen extends AbstractSimiContainerScreen<MaidAssemblyMenu> {

    private static final int BORDER = 0xFF555555, FILL = 0xFFC6C6C6;
    private static final int SLOT_BORDER = 0xFF373737, SLOT_INNER = 0xFF8B8B8B;
    private static final int PANEL_H = 100, PANEL_W = 176;

    private List<Rect2i> extraAreas = Collections.emptyList();

    public MaidAssemblyScreen(MaidAssemblyMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(PANEL_W, PANEL_H + 4 + 92);
        super.init();

        int btnY = topPos + 78;

        // 材料锁定按钮 — 通过 clickMenuButton 发包到服务端
        int lockX = leftPos + MaidAssemblyMenu.MACHINE_X;
        addRenderableWidget(new MatLockButton(lockX, btnY));

        // 确认
        IconButton confirm = new IconButton(leftPos + PANEL_W - 18 - 8, btnY, AllIcons.I_CONFIRM);
        confirm.withCallback(() -> {
            if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
        });
        addRenderableWidget(confirm);

        extraAreas = ImmutableList.of(
            new Rect2i(leftPos + PANEL_W, topPos + PANEL_H - 56, 64, 56));
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int l = leftPos, t = topPos;
        int sx = MaidAssemblyMenu.MACHINE_X, ss = MaidAssemblyMenu.SLOT_SIZE;

        // 面板
        drawPanel(g, l, t, PANEL_W, PANEL_H);
        g.drawString(font, title, l + 8, t + 6, 0xFF404040, false);

        // 12装配槽位背景
        for (int i = 0; i < MaidAssemblyMenu.MACHINE_SLOTS; i++)
            drawSlotBg(g, l + sx + i * ss, t + MaidAssemblyMenu.MACHINE_Y);
        drawSlotBg(g, l + sx, t + MaidAssemblyMenu.BOTTOM_Y);
        drawSlotBg(g, l + sx + ss, t + MaidAssemblyMenu.BOTTOM_Y);
        drawSlotBg(g, l + sx + 6 * ss, t + MaidAssemblyMenu.BOTTOM_Y);
        drawSlotBg(g, l + sx + 7 * ss, t + MaidAssemblyMenu.BOTTOM_Y);

        // 标签
        int ly = t + MaidAssemblyMenu.LABEL_Y;
        g.drawString(font, "🧱", l + sx + 1, ly, 0xFF404040, false);
        g.drawString(font, "中", l + sx + ss + 3, ly, 0xFF404040, false);
        g.drawString(font, "📦", l + sx + 6 * ss + 1, ly, 0xFF404040, false);

        // 玩家栏
        int invTop = t + PANEL_H + 4;
        g.drawString(font, playerInventoryTitle, l + 8, invTop, 0xFF404040, false);
        int py = invTop + 9;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                drawSlotBg(g, l + 8 + col * 18, py + row * 18);
        for (int col = 0; col < 9; col++)
            drawSlotBg(g, l + 8 + col * 18, py + 58);
    }

    static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
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

    static void drawSlotBg(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_BORDER);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_INNER);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override
    public List<Rect2i> getExtraAreas() { return extraAreas; }

    // ── 材料锁定按钮（仿Biotech LockIconButton） ──

    private class MatLockButton extends IconButton {
        MatLockButton(int x, int y) {
            super(x, y, AllIcons.I_CONFIG_UNLOCKED);
            withCallback(() -> Minecraft.getInstance().gameMode
                .handleInventoryButtonClick(menu.containerId, MaidAssemblyMenu.LOCK_BUTTON_ID));
        }

        @Override
        public void doRender(GuiGraphics g, int mx, int my, float pt) {
            setIcon(menu.isMatLocked() ? AllIcons.I_CONFIG_LOCKED : AllIcons.I_CONFIG_UNLOCKED);
            super.doRender(g, mx, my, pt);
        }
    }
}
