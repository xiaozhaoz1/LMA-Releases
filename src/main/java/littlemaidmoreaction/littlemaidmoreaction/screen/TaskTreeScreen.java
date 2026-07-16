package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.task.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 任务树 GUI (v35.5: 修复 render() 中 addRenderableWidget 每帧叠加 + 右侧步骤溢出) */
public final class TaskTreeScreen extends Screen {

    private final Screen parent;
    private List<TaskTree.TaskNode> nodes;
    private int selectedIdx = -1, scroll = 0;
    private static final int ROW_H = 32, LEFT_W = 180;

    public TaskTreeScreen(Screen parent) {
        super(Component.literal("LMA 任务树"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        nodes = TaskTree.build();
        if (selectedIdx >= nodes.size()) selectedIdx = nodes.isEmpty() ? -1 : 0;

        // 关闭按钮
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
            .pos(this.width - 80, this.height - 28).size(70, 20).build());

        // 切换按钮 — init() 中创建(仅首次打开/resize)，非 render() 每帧创建
        if (selectedIdx >= 0 && selectedIdx < nodes.size()) {
            var n = nodes.get(selectedIdx);
            int btnY = 22 + 16 + 12 + 16 + 8; // header area: taskType + enabled + visible + gap
            addRenderableWidget(Button.builder(
                Component.literal(n.enabled() ? "禁用" : "启用"),
                b -> { TaskToggle.setEnabled(n.taskType(), !n.enabled()); clearWidgets(); init(); })
                .pos(LEFT_W + 14, btnY).size(50, 16).build());
            addRenderableWidget(Button.builder(
                Component.literal(n.visible() ? "隐藏" : "显示"),
                b -> { TaskToggle.setVisible(n.taskType(), !n.visible()); clearWidgets(); init(); })
                .pos(LEFT_W + 69, btnY).size(50, 16).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "§6LMA 任务树 §7(" + nodes.size() + ")", this.width / 2, 6, 0xFFD700);

        int lx = 10, ly = 22, lh = this.height - 52, dx = lx + LEFT_W + 4;
        int rightW = this.width - dx - 8; // 右侧可用宽度

        // 左侧列表
        g.fill(lx, ly, lx + LEFT_W, ly + lh, 0xAA1A1A1A);
        g.renderOutline(lx, ly, LEFT_W, lh, 0xFF666666);
        g.enableScissor(lx + 2, ly + 2, lx + LEFT_W - 2, ly + lh - 2);
        int ry = ly + 4 + scroll;
        for (int i = 0; i < nodes.size(); i++) {
            if (ry + ROW_H < ly || ry > ly + lh) { ry += ROW_H; continue; }
            var n = nodes.get(i);
            boolean sel = i == selectedIdx;
            boolean hov = mx >= lx && mx <= lx + LEFT_W && my >= ry && my <= ry + ROW_H;
            if (sel) g.fill(lx + 3, ry, lx + LEFT_W - 3, ry + ROW_H, 0x553355AA);
            else if (hov) g.fill(lx + 3, ry, lx + LEFT_W - 3, ry + ROW_H, 0x33333333);
            g.drawString(font, (n.enabled() ? "§a" : "§c") + "● " + (n.visible() ? "§f" : "§8") + n.taskType(), lx + 8, ry + 2, 0xFFFFFF);
            g.drawString(font, "§7" + n.steps().size() + "步骤" + (n.visible() ? "" : " 隐藏"), lx + 12, ry + 16, 0xAAAAAA);
            ry += ROW_H;
        }
        g.disableScissor();

        // 右侧详情 (v35.5: 按钮已移到 init(), 步骤区加 scissor 防止溢出)
        if (selectedIdx >= 0 && selectedIdx < nodes.size()) {
            var n = nodes.get(selectedIdx);
            int dy = ly;
            g.drawString(font, "§6" + n.taskType(), dx, dy, 0xFFD700); dy += 16;
            g.drawString(font, "§7启用: " + (n.enabled() ? "§a是" : "§c否"), dx, dy, 0xFFFFFF); dy += 12;
            g.drawString(font, "§7显示在任务栏: " + (n.visible() ? "§a是" : "§8否(被动)"), dx, dy, 0xFFFFFF);
            dy += 16 + 8; // gap before buttons (buttons at dy in init())

            // 步骤区 (按钮下方, scissor 裁剪防溢出)
            if (!n.steps().isEmpty()) {
                int stepsY = dy + 22; // buttons: 16px height + 6px gap
                int stepsH = (ly + lh) - stepsY;
                if (stepsH > 20) {
                    g.drawString(font, "§6步骤:", dx, stepsY, 0xFFD700); stepsY += 12;
                    g.enableScissor(dx, stepsY + 1, dx + rightW, ly + lh - 2);
                    for (var s : n.steps()) {
                        if (stepsY + 12 >= ly && stepsY <= ly + lh)
                            g.drawString(font, "  §7[" + s.type() + "] §f" + s.label(), dx + 4, stepsY, 0xFFFFFF);
                        stepsY += 12;
                    }
                    g.disableScissor();
                }
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int ly = 22, lh = this.height - 52;
        if (mx >= 10 && mx <= 10 + LEFT_W && my >= ly && my <= ly + lh) {
            int idx = (int)((my - ly - 4 - scroll) / ROW_H);
            if (idx >= 0 && idx < nodes.size()) {
                selectedIdx = idx;
                clearWidgets(); init(); // 重建按钮以匹配新选中项
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll += (int)(delta * 20);
        int min = -(nodes.size() * ROW_H - (this.height - 52) + 30);
        if (scroll > 0) scroll = 0; else if (scroll < min) scroll = min;
        return true;
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
