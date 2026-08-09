package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.ConfigSyncPacket;

/**
 * 独立配置屏幕 — Forge 模组列表点本模组即可打开，不依赖 TLM。
 * (v72 Phase 5: 规则引擎按钮/规则数已移除 — 规则系统退役)
 * (v79.25.2: TLM 256×256 棕色主面板背景 + 按钮排进面板 — 用户裁定泥土背景风)
 * (v79.26.3: 去 TLM 棕面板 — 背景统一原版旋转全景
 * {@link PanoramaBackground}, 按钮组直接浮全景上)
 */
public final class LMAConfigScreen extends Screen {
    private final Screen parent;

    public LMAConfigScreen(Screen parent) {
        super(Component.literal("LittleMaidMoreAction"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        // v79.26.3: 去 TLM 棕面板 — 按钮组直接浮全景上, 居中排列 (6 按钮 × 28 间距)
        int y = this.height / 2 - 75;

        // 详细设置 (v73: Cloth Config 软依赖 — 未安装时提示而非崩溃, 崩溃实测 crash-2026-08-02_20.08.56)
        this.addRenderableWidget(Button.builder(
                Component.literal("详细设置"),
                btn -> {
                    if (!isClothLoaded()) {
                        var player = Minecraft.getInstance().player;
                        if (player != null) {
                            player.displayClientMessage(
                                    Component.literal("§c需要安装 Cloth Config 才能打开详细设置"), false);
                        }
                        return;
                    }
                    Minecraft.getInstance().setScreen(ClothSettingsScreen.create(this));
                })
                .pos(cx - 80, y).size(160, 20).build());
        y += 28;

        // ★ v35.2: 任务树入口
        this.addRenderableWidget(Button.builder(
                Component.literal("任务树"),
                btn -> Minecraft.getInstance().setScreen(new TaskTreeScreen(this)))
                .pos(cx - 80, y).size(160, 20).build());
        y += 28;

        // v79.25: 女仆独立选择入口 (独立屏, 非 TLM 容器屏)
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.littlemaidmoreaction.maid_list.button"),
                btn -> Minecraft.getInstance().setScreen(new MaidListScreen(this)))
                .pos(cx - 80, y).size(160, 20).build());
        y += 28;

        // v77: 兼容模块开关 (注册期生效 — 重启后应用; 同包免 import)
        this.addRenderableWidget(Button.builder(
                Component.literal("兼容模块"),
                btn -> Minecraft.getInstance().setScreen(new CompatConfigScreen(this)))
                .pos(cx - 80, y).size(160, 20).build());
        y += 28;


        this.addRenderableWidget(Button.builder(
                Component.literal("调试: " + (MoreActionConfig.DEBUG_MODE.get() ? "ON" : "OFF")),
                btn -> {
                    boolean v = !MoreActionConfig.DEBUG_MODE.get();
                    MoreActionConfig.DEBUG_MODE.set(v);
                    MoreActionConfig.saveAll();
                    if (!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()) {
                        ConfigSyncPacket.send();
                    }
                    btn.setMessage(Component.literal("调试: " + (v ? "ON" : "OFF")));
                }).pos(cx - 80, y).size(160, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("完成"),
                btn -> Minecraft.getInstance().setScreen(parent))
                .pos(cx - 40, y + 140).size(80, 20).build());
    }

    /** Cloth Config 软依赖检测 (v73: 未安装时详细设置不可用 — 双平台) */
    private static boolean isClothLoaded() {
//? if 1.20.1 {
        return net.minecraftforge.fml.ModList.get().isLoaded("cloth_config");
//?} else {
        return net.neoforged.fml.ModList.get().isLoaded("cloth_config");
//?}
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        // v79.26.3: 去 TLM 棕面板 blit — 标题/按钮直接浮全景上
        g.drawCenteredString(font, "LittleMaidMoreAction 配置", width / 2, 20, 0xFFD700);
        super.render(g, mx, my, pt);
    }

    /** v79.26.3: 原版主菜单旋转全景背景 (统一 {@link PanoramaBackground}, 去 TLM 深棕渐变)。
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
