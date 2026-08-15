package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatRegistry;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

//? if 1.20.1 {
import net.minecraftforge.fml.ModList;
//?} else {
import net.neoforged.fml.ModList;
//?}

/**
 * 兼容模块开关 GUI (v77) — 任务树同款两栏布局 (v77.2 重写)。
 *
 * <p>布局: 左列表 (滚动, 全部模块含未装灰显) + 右侧详情 (状态/描述/开关按钮固定区,
 * 选中项变化重建 — 按钮不随滚动移动)。开关写入 compat_toggles.json, 注册期生效 → 重启应用。
 * 无 menu/网络 — 仅文件持久化。
 *
 * <p>★ 渲染纪律 (错题集): 颜色全放 Component withColor (v73 — 5 参 drawString 双平台
 * 渲染不保证); 覆写 renderBackground (v75.2 — 1.21 默认 renderBackground 是模糊压黑背景,
 * 文字在模糊背景上发虚 — 本屏 v77.2 实测修复); //? 条件指令行禁尾随注释。
 */
public final class CompatConfigScreen extends Screen {

    private final Screen parent;
    private java.util.List<CompatRegistry.CompatModule> modules;
    private int selectedIdx = 0, scroll = 0;
    private static final int ROW_H = 32, LEFT_W = 180;

    public CompatConfigScreen(Screen parent) {
        super(Component.translatable("screen.littlemaidmoreaction.compat"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        modules = CompatRegistry.getAllModules();
        if (selectedIdx >= modules.size()) selectedIdx = modules.isEmpty() ? -1 : 0;

        // 关闭按钮
        addRenderableWidget(Button.builder(Component.translatable("gui.littlemaidmoreaction.compat.back"), b -> onClose())
                .pos(this.width - 80, this.height - 28).size(70, 20).build());

        // 右侧详情: 开关按钮 (仅已装 mod; 选中项变化时重建, 不随列表滚动)
        if (selectedIdx >= 0 && selectedIdx < modules.size()) {
            var m = modules.get(selectedIdx);
            if (isModLoaded(m.modId())) {
                int btnY = 40 + 16 + 12 + 16 + 16 + 16;   // name + status + 依赖 + 描述 + 红字区 + gap (ly 40 对齐)
                addRenderableWidget(Button.builder(
                        Component.translatable(CompatToggle.isModuleEnabled(m.id())
                                ? "gui.littlemaidmoreaction.compat.disable"
                                : "gui.littlemaidmoreaction.compat.enable"),
                        b -> {
                            CompatToggle.setModuleEnabled(m.id(), !CompatToggle.isModuleEnabled(m.id()));
                            clearWidgets(); init();
                        })
                        .pos(LEFT_W + 14, btnY).size(60, 20).build());
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        g.drawCenteredString(font, Component.translatable("screen.littlemaidmoreaction.compat")
                .withStyle(s -> s.withColor(0xFFD700)), this.width / 2, 6, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.littlemaidmoreaction.compat.restart_hint")
                .withStyle(s -> s.withColor(0xFFAA55)), this.width / 2, 24, 0xFFFFFF);

        int lx = 10, ly = 40, lh = this.height - 72, dx = lx + LEFT_W + 4;   // ly 40 避提示行 (y=24) 重叠

        // 左侧列表 (scissor — 溢出行裁剪)
        g.fill(lx, ly, lx + LEFT_W, ly + lh, 0xAA1A1A1A);
        g.renderOutline(lx, ly, LEFT_W, lh, 0xFF666666);
        g.enableScissor(lx + 2, ly + 2, lx + LEFT_W - 2, ly + lh - 2);
        int ry = ly + 4 + scroll;
        for (int i = 0; i < modules.size(); i++) {
            if (ry + ROW_H < ly || ry > ly + lh) { ry += ROW_H; continue; }
            var m = modules.get(i);
            boolean sel = i == selectedIdx;
            boolean hov = mx >= lx && mx <= lx + LEFT_W && my >= ry && my <= ry + ROW_H;
            if (sel) g.fill(lx + 3, ry, lx + LEFT_W - 3, ry + ROW_H, 0x553355AA);
            else if (hov) g.fill(lx + 3, ry, lx + LEFT_W - 3, ry + ROW_H, 0x33333333);
            // 三态 (合并单次计算 — 双 withStyle 后者赢)
            boolean loaded = isModLoaded(m.modId());
            boolean enabled = loaded && CompatToggle.isModuleEnabled(m.id());
            int rowColor = !loaded ? 0x888888 : (enabled ? 0x55FF55 : 0xFF5555);
            g.drawString(font, Component.literal("● " + m.id())
                    .withStyle(s -> s.withColor(rowColor)), lx + 8, ry + 2, 0xFFFFFF);
            g.drawString(font, Component.literal(m.name())
                    .withStyle(s -> s.withColor(0xAAAAAA)), lx + 12, ry + 16, 0xFFFFFF);
            ry += ROW_H;
        }
        g.disableScissor();

        // 右侧详情
        if (selectedIdx >= 0 && selectedIdx < modules.size()) {
            var m = modules.get(selectedIdx);
            boolean loaded = isModLoaded(m.modId());
            boolean enabled = loaded && CompatToggle.isModuleEnabled(m.id());
            int dy = ly;
            g.drawString(font, Component.literal(m.name()).withStyle(s -> s.withColor(0xFFD700)), dx, dy, 0xFFFFFF); dy += 16;
            g.drawString(font, Component.translatable("gui.littlemaidmoreaction.compat.status")
                    .withStyle(s -> s.withColor(0xAAAAAA))
                    .append(Component.translatable(!loaded ? "gui.littlemaidmoreaction.compat.not_installed"
                                    : (enabled ? "gui.littlemaidmoreaction.compat.enabled" : "gui.littlemaidmoreaction.compat.disabled"))
                            .withStyle(s -> s.withColor(!loaded ? 0x888888 : (enabled ? 0x55FF55 : 0xFF5555)))),
                    dx, dy, 0xFFFFFF); dy += 12;
            g.drawString(font, Component.translatable("gui.littlemaidmoreaction.compat.dependency", m.modId())
                    .withStyle(s -> s.withColor(0x888888)), dx, dy, 0xFFFFFF); dy += 16 + 8;
            g.drawString(font, Component.literal(m.description())
                    .withStyle(s -> s.withColor(0xAAAAAA)), dx, dy, 0xFFFFFF);
            if (!loaded) {
                dy += 16;
                g.drawString(font, Component.translatable("gui.littlemaidmoreaction.compat.unavailable")
                        .withStyle(s -> s.withColor(0xFF5555)), dx, dy, 0xFFFFFF);
            }
        }
        super.render(g, mx, my, pt);
    }

    /** 原版主菜单旋转全景背景 (统一 {@link PanoramaBackground}, 去 TLM 深棕渐变衬底)。
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
        int ly = 40, lh = this.height - 72;   // 与 render 对齐
        if (mx >= 10 && mx <= 10 + LEFT_W && my >= ly && my <= ly + lh) {
            int idx = (int) ((my - ly - 4 - scroll) / ROW_H);
            if (idx >= 0 && idx < modules.size()) {
                selectedIdx = idx;
                clearWidgets(); init();
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
        scroll += (int) (delta * 20);
        int min = -(modules.size() * ROW_H - (this.height - 52) + 30);
        if (scroll > 0) scroll = 0; else if (scroll < min) scroll = min;
        return true;
    }

    /** 双平台 mod 存在性 (LMAConfigScreen.isClothLoaded 模式) */
    private static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
