package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.tartaricacid.touhoulittlemaid.api.client.gui.ITooltipButton;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * v79.24.1: 女仆容器 GUI 注入按钮 — 双样式 (顶部 tab / 右侧侧栏)。
 * 纹理复用 TLM maid_gui_side.png (SIDE 纹理); 图标 Items.BOOK; tooltip 经 ITooltipButton
 * 由 TLM 基类 renderTooltip 自动扫描渲染。
 * <p>参照: TLM {@code MaidSideTabButton} (侧栏规格) + MaidAttributeDisplay (顶部 tab 规格)。</p>
 */
public class MaidListButton extends Button implements ITooltipButton {

    private static final ResourceLocation SIDE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "textures/gui/maid_gui_side.png");
    private static final ItemStack ICON = new ItemStack(Items.BOOK);

    public enum Kind {
        /** 顶部 tab (leftPos+194, topPos+5) — 背景 blit x+0 UV (107,21), 图标 (x+4, y+6) */
        TAB(24, 26, 107, 21, 0, 4, 6),
        /** 右侧侧栏 (leftPos+251, TLM MaidSideTabs) — 背景 blit x+2 UV (209,157), 图标 (x+6, y+4) */
        SIDE(26, 24, 209, 157, 2, 6, 4);

        final int width;
        final int height;
        final int bgU;
        final int bgV;
        final int bgX;
        final int iconX;
        final int iconY;

        Kind(int width, int height, int bgU, int bgV, int bgX, int iconX, int iconY) {
            this.width = width;
            this.height = height;
            this.bgU = bgU;
            this.bgV = bgV;
            this.bgX = bgX;
            this.iconX = iconX;
            this.iconY = iconY;
        }
    }

    private final Kind kind;
    private final List<Component> tooltips;

    public MaidListButton(int x, int y, Kind kind, boolean active, OnPress onPress, List<Component> tooltips) {
        super(Button.builder(Component.empty(), onPress).pos(x, y).size(kind.width, kind.height));
        this.kind = kind;
        this.active = active;
        this.tooltips = tooltips;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.enableDepthTest();
        // 背景: SIDE 始终绘制 (v79.25 用户裁定 "加右侧按钮背景" — 侧栏按钮必须可见);
        // TAB 仅非激活态绘制 (与 TLM MaidSideTabButton 一致 — 激活态由 TLM 自身背景承载)
        if (kind == Kind.SIDE || !this.active) {
            graphics.blit(SIDE_TEXTURE, this.getX() + kind.bgX, this.getY(),
                    kind.bgU, kind.bgV, this.width, this.height, 256, 256);
        }
        graphics.renderItem(ICON, this.getX() + kind.iconX, this.getY() + kind.iconY);
    }

    @Override
    public boolean isTooltipHovered() {
        // 与 TLM MaidSideTabButton / MaidAttributeDisplay 一致: 无 active 条件 —
        // 占位按钮 (active=false) hover 也显示 tooltip
        return this.isHovered();
    }

    @Override
    public void renderTooltip(GuiGraphics graphics, Minecraft mc, int mouseX, int mouseY) {
        graphics.renderComponentTooltip(mc.font, this.tooltips, mouseX, mouseY);
    }
}
