package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v79.26.3: 原版主菜单旋转全景背景 — 全 LMA 屏统一 (去 TLM 棕面板)。
 * <p>1.21.1 用 Screen 内置 renderPanorama (protected — 各屏 renderBackground override 里
 * {@code this.renderPanorama(g, pt)}, v79.25 LMAConfigScreen 同款, 原版全景+vignette 暗角
 * 用户环境实测可工作); 1.20.1 Screen 无 renderPanorama (client_mappings 实证) →
 * 此处 PanoramaRenderer 直渲兜底 (双平台类均在 net.minecraft.client.renderer 包, decompile 实证;
 * 构造安全: MainMenuScreen 静态字段同款)。
 * <p>不调 super.renderBackground: 1.21 默认含 renderBlurredBackground 背景模糊 (用户明确
 * "记得去模糊 — 去模糊是为了让字不模糊")。错题 #138。
 */
public final class PanoramaBackground {

    //? if 1.20.1 {
    private static final ResourceLocation TITLE_BACKGROUND_LOCATION =
            new ResourceLocation("textures/gui/title/background/panorama");
    private static final PanoramaRenderer PANORAMA =
            new PanoramaRenderer(new CubeMap(TITLE_BACKGROUND_LOCATION));

    public static void render(GuiGraphics g) {
        PANORAMA.render(1.0F, 1.0F);
    }
    //?}

    private PanoramaBackground() {
    }
}
