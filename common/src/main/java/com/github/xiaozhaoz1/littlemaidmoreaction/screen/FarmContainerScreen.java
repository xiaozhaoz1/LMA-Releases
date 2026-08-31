package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.network.FarmContainerBindPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * 统一容器绑定菜单 (v79.62) — 木棍右键容器时打开, 4 角色选择:
 * 种子源 / 收获目标 (farm) + 取出源 / 放入目标 (arm_transfer).
 * 选择 → C2S {@link FarmContainerBindPacket} → 服务端写木棍 NBT → 右键女仆交付.
 */
public final class FarmContainerScreen extends Screen {

    private final BlockPos pos;

    public FarmContainerScreen(BlockPos pos) {
        super(Component.translatable("screen.littlemaidmoreaction.farm_container"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int w = 200, h = 20, gap = 6;
        int y = cy - 2 * (h + gap);
        addButton(cx - w / 2, y, "screen.littlemaidmoreaction.farm_container.seed", "seed");
        y += h + gap;
        addButton(cx - w / 2, y, "screen.littlemaidmoreaction.farm_container.harvest", "harvest");
        y += h + gap;
        addButton(cx - w / 2, y, "screen.littlemaidmoreaction.farm_container.take", "take");
        y += h + gap;
        addButton(cx - w / 2, y, "screen.littlemaidmoreaction.farm_container.deposit", "deposit");
        y += h + gap + 8;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                btn -> Minecraft.getInstance().setScreen(null))
                .bounds(cx - w / 2, y, w, h).build());
    }

    private void addButton(int x, int y, String key, String role) {
        this.addRenderableWidget(Button.builder(
                Component.translatable(key),
                btn -> {
                    FarmContainerBindPacket.sendToServer(role, pos);
                    Minecraft.getInstance().setScreen(null);
                })
                .bounds(x, y, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        g.drawCenteredString(font, Component.translatable("screen.littlemaidmoreaction.farm_container.title")
                .copy().append(" §7" + pos.toShortString()), this.width / 2, this.height / 2 - 70, 0xFFFFE8D9);
        super.render(g, mx, my, pt);
    }

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
