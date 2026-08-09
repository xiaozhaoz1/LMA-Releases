package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.data.TaskToggle;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.TaskTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 任务树 GUI (v35.5: 修复 render() 中 addRenderableWidget 每帧叠加 + 右侧步骤溢出;
 * v73 修复: 颜色全放 Component withColor - 5 参 drawString 双平台编译, 样式渲染必然生效;
 * v74.5 修复: 双 withStyle 颜色覆盖 (后者赢) → 合并单次计算; 暗色文字 0x555555 → 0x888888;
 * v75.2 修复: 1.21 默认 renderBackground 是模糊压黑背景 (super.render 尾部必调覆盖自绘)
 * → 覆写 renderBackground 自绘不透明白底)。
 * 注意: //? 条件指令行禁止尾随注释 (stonecutter 词法报错, 实测)。
 */
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

        // 切换按钮 - init() 中创建(仅首次打开/resize)，非 render() 每帧创建
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
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        g.drawCenteredString(font, Component.literal("LMA 任务树 (")
                .withStyle(s -> s.withColor(0xFFD700))
                .append(Component.literal(String.valueOf(nodes.size())).withStyle(s -> s.withColor(0xAAAAAA)))
                .append(Component.literal(")").withStyle(s -> s.withColor(0xFFD700))),
                this.width / 2, 6, 0xFFFFFF);

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
            // v74.5: 双 withStyle 颜色覆盖 (后者赢) - 合并为单次计算 (启用绿/禁用红, 隐藏降灰)
            int rowColor = !n.visible() ? 0x888888 : (n.enabled() ? 0x55FF55 : 0xFF5555);
            g.drawString(font, Component.literal("● " + n.taskType())
                    .withStyle(s -> s.withColor(rowColor)),
                    lx + 8, ry + 2, 0xFFFFFF);
            g.drawString(font, Component.literal(n.steps().size() + "步骤" + (n.visible() ? "" : " 隐藏"))
                    .withStyle(s -> s.withColor(0xAAAAAA)), lx + 12, ry + 16, 0xFFFFFF);
            ry += ROW_H;
        }
        g.disableScissor();

        // 右侧详情
        if (selectedIdx >= 0 && selectedIdx < nodes.size()) {
            var n = nodes.get(selectedIdx);
            int dy = ly;
            g.drawString(font, Component.literal(n.taskType()).withStyle(s -> s.withColor(0xFFD700)), dx, dy, 0xFFFFFF); dy += 16;
            g.drawString(font, Component.literal("启用: ")
                    .withStyle(s -> s.withColor(0xAAAAAA))
                    .append(Component.literal(n.enabled() ? "是" : "否")
                            .withStyle(s -> s.withColor(n.enabled() ? 0x55FF55 : 0xFF5555))),
                    dx, dy, 0xFFFFFF); dy += 12;
            g.drawString(font, Component.literal("显示在任务栏: ")
                    .withStyle(s -> s.withColor(0xAAAAAA))
                    .append(Component.literal(n.visible() ? "是" : "否(被动)")
                            .withStyle(s -> s.withColor(n.visible() ? 0x55FF55 : 0x888888))),
                    dx, dy, 0xFFFFFF);
            dy += 16 + 8; // gap before buttons (buttons at dy in init())

            // 步骤区 (按钮下方, scissor 裁剪防溢出)
            if (!n.steps().isEmpty()) {
                int stepsY = dy + 22; // buttons: 16px height + 6px gap
                int stepsH = (ly + lh) - stepsY;
                if (stepsH > 20) {
                    g.drawString(font, Component.literal("步骤:").withStyle(s -> s.withColor(0xFFD700)), dx, stepsY, 0xFFFFFF); stepsY += 12;
                    g.enableScissor(dx, stepsY + 1, dx + rightW, ly + lh - 2);
                    for (var s : n.steps()) {
                        if (stepsY + 12 >= ly && stepsY <= ly + lh)
                            g.drawString(font, Component.literal("  [" + s.type() + "] " + s.label())
                                    .withStyle(st -> st.withColor(0xAAAAAA)), dx + 4, stepsY, 0xFFFFFF);
                        stepsY += 12;
                    }
                    g.disableScissor();
                }
            }
        }
        super.render(g, mx, my, pt);
    }

    /** v79.26.3: 原版主菜单旋转全景背景 (统一 {@link PanoramaBackground}, 去 TLM 深棕渐变衬底)。
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
//? if 1.20.1 {
    public boolean mouseScrolled(double mx, double my, double delta) {
//?} else {
    public boolean mouseScrolled(double mx, double my, double dx, double delta) {
//?}
        scroll += (int)(delta * 20);
        int min = -(nodes.size() * ROW_H - (this.height - 52) + 30);
        if (scroll > 0) scroll = 0; else if (scroll < min) scroll = min;
        return true;
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
