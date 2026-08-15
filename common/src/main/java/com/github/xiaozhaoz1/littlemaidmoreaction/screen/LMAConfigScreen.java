package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 独立配置屏幕 — Forge 模组列表点本模组即可打开，不依赖 TLM。
 * (v72 Phase 5: 规则引擎按钮/规则数已移除 — 规则系统退役)
 * (v79.25.2: TLM 256×256 棕色主面板背景 + 按钮排进面板 — 用户裁定泥土背景风)
 * (v79.26.3: 去 TLM 棕面板 — 背景统一原版旋转全景
 * {@link PanoramaBackground}, 按钮组直接浮全景上)
 * (v79.51: 6 按钮数据驱动 — {@link ScreenRegistry#configButtons()} 枚举,
 * 注册序 = 显示序, 位置/尺寸/行为不变)
 */
public final class LMAConfigScreen extends Screen {
    /** 原始父屏 (打开本屏的屏) — 包私有: ScreenRegistry done 按钮条目经此返回 */
    final Screen parent;

    public LMAConfigScreen(Screen parent) {
        super(Component.translatable("screen.littlemaidmoreaction.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        // 去 TLM 棕面板 — 按钮组直接浮全景上, 居中排列 (6 按钮 × 28 间距)
        int y = this.height / 2 - 75;

        // 按钮表枚举 — 常规列 160×20 (y+idx*28), done 条目按 name 独立定位 (80×20, y+140)
        // (name 判定而非末位 — 外部 registerConfigButton 追加不挤乱 done 独立位)
        var buttons = ScreenRegistry.configButtons();
        for (int i = 0; i < buttons.size(); i++) {
            ScreenRegistry.ConfigButton spec = buttons.get(i);
            boolean done = "done".equals(spec.name());
            int bx = done ? cx - 40 : cx - 80;
            int by = done ? y + 140 : y + i * 28;
            this.addRenderableWidget(Button.builder(spec.title(),
                    btn -> spec.onPress().accept(btn, this))
                    .pos(bx, by).size(done ? 80 : 160, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        // 去 TLM 棕面板 blit — 标题/按钮直接浮全景上
        g.drawCenteredString(font, Component.translatable("screen.littlemaidmoreaction.config"),
                width / 2, 20, 0xFFD700);
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
}
