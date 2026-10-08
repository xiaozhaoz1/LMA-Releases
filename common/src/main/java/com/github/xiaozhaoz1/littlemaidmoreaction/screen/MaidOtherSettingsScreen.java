package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidGomokuStatePacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidGomokuTogglePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * v79.62.3: 女仆「其他设置」屏 — 从 MaidListScreen「其他设置」按钮进入。
 * 内容: 五子棋「直接判你赢」开关 (存女仆 PD)。
 *
 * <p>开关交互 (错题集模式 — 点了后台真变 + 界面回显):
 * 打开时发 QUERY → 服务端回 {@link MaidGomokuStatePacket} (静态缓存);
 * 点按钮发 TOGGLE → 服务端翻 PD → 回状态包 → 本屏 tick 轮询缓存刷新按钮文字。
 *
 * <p>布局 (400×240 面板, 仿 MaidFarmRegionScreen):
 *   标题 居中 y=py+12 / 女仆名 y=py+26
 *   说明 y=py+70 / 开关按钮 (居中, 宽 160) y=py+96
 *   返回按钮 (右下) x=px+16..146 y=py+204
 */
public final class MaidOtherSettingsScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 240;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_SUB = 0xFFCCCCCC;

    private final Screen parent;
    private final String maidUuid;
    private final String maidName;
    private Button gomokuToggle;

    public MaidOtherSettingsScreen(Screen parent, String maidUuid, String maidName) {
        super(Component.translatable("screen.littlemaidmoreaction.other_settings"));
        this.parent = parent;
        this.maidUuid = maidUuid;
        this.maidName = maidName;
    }

    @Override
    protected void init() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        // 打开即查当前开关状态 (服务端回 MaidGomokuStatePacket)
        MaidGomokuTogglePacket.sendToServer(MaidGomokuTogglePacket.ACTION_QUERY, java.util.UUID.fromString(maidUuid));
        // 五子棋直接判你赢 开关
        this.gomokuToggle = this.addRenderableWidget(Button.builder(
                Component.literal(""), btn -> onToggleGomoku())
                .pos(px + (PANEL_W - 160) / 2, py + 96).size(160, 20).build());
        // 返回
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                btn -> Minecraft.getInstance().setScreen(parent))
                .pos(px + 16, py + PANEL_H - 36).size(130, 20).build());
        refreshToggleLabel();
    }

    @Override
    public void tick() {
        super.tick();
        refreshToggleLabel();
    }

    private void onToggleGomoku() {
        MaidGomokuTogglePacket.sendToServer(MaidGomokuTogglePacket.ACTION_TOGGLE,
                java.util.UUID.fromString(maidUuid));
    }

    private void refreshToggleLabel() {
        boolean on = MaidGomokuStatePacket.getLastState();
        this.gomokuToggle.setMessage(Component.translatable(on
                ? "gui.littlemaidmoreaction.other_settings.gomoku.on"
                : "gui.littlemaidmoreaction.other_settings.gomoku.off"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        this.renderBackground(g);
//?} else {
        this.renderBackground(g, mx, my, pt);
//?}
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        g.drawCenteredString(this.font, title, this.width / 2, py + 12, COLOR_TEXT);
        g.drawCenteredString(this.font, Component.literal(maidName), this.width / 2, py + 26, COLOR_SUB);
        g.drawCenteredString(this.font, Component.translatable(
                "gui.littlemaidmoreaction.other_settings.gomoku.desc"), this.width / 2, py + 70, COLOR_SUB);
        super.render(g, mx, my, pt);
    }

    /** GUI 规范 (错题集): 覆写 renderBackground 用透明背景 — 1.21 默认渐变压黑 → 字模糊 */
    @Override
//? if 1.20.1 {
    public void renderBackground(GuiGraphics g) {
        PanoramaBackground.render(g);
    }
//?} else {
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        this.renderPanorama(g, pt);
    }
//?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
