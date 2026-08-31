package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.network.FarmRegionEditPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage;
import com.github.xiaozhaoz1.littlemaidmoreaction.storage.FarmRegionStorage.FarmRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class MaidFarmRegionDetailScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 220;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_SUB = 0xFFCCCCCC;
    private static final int COLOR_SEL = 0x553355AA;
    private final Screen parent;
    private final String maidUuid;
    private final int index;
    private final FarmRegion region;
    private String name;
    private String cropId;
    private boolean rightHarvest;
    private boolean dropdownOpen;
    private List<String> seeds;
    private int dropdownScroll;
    private EditBox nameBox;
    private EditBox cropBox;
    private Button modeButton;

    public MaidFarmRegionDetailScreen(Screen parent, String maidUuid, int index, FarmRegion region) {
        super(Component.translatable("screen.littlemaidmoreaction.farm_region.detail"));
        this.parent = parent;
        this.maidUuid = maidUuid;
        this.index = index;
        this.region = region;
        this.name = region.displayName();
        this.cropId = region.cropId() == null ? "" : region.cropId();
        this.rightHarvest = region.isRightHarvest();
        this.seeds = SeedPickerScreen.allSeedIds();
    }

    @Override
    protected void init() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        this.nameBox = new EditBox(this.font, px + 90, py + 30, 200, 16, Component.literal("name"));
        this.nameBox.setMaxLength(64);
        this.nameBox.setValue(this.name);
        this.addRenderableWidget(this.nameBox);
        // v79.62.1 修复: 种子 id 移到收获方式下方 (下拉框不被挡)
        this.modeButton = this.addRenderableWidget(Button.builder(
                Component.literal(this.rightHarvest ? "右键收" : "左键收"),
                btn -> { rightHarvest = !rightHarvest; modeButton.setMessage(Component.literal(rightHarvest ? "右键收" : "左键收")); })
                .pos(px + 90, py + 54).size(80, 16).build());
        this.cropBox = new EditBox(this.font, px + 90, py + 78, 170, 16, Component.literal("crop"));
        this.cropBox.setMaxLength(64);
        this.cropBox.setValue(this.cropId);
        this.addRenderableWidget(this.cropBox);
        this.addRenderableWidget(Button.builder(Component.literal("V"),
                btn -> { dropdownOpen = !dropdownOpen; dropdownScroll = 0; })
                .pos(px + 264, py + 78).size(26, 16).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.littlemaidmoreaction.farm_region.save"),
                btn -> save()).pos(px + (PANEL_W - 100) / 2, py + PANEL_H - 28).size(100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                btn -> Minecraft.getInstance().setScreen(parent))
                .pos(px + 16, py + PANEL_H - 28).size(60, 20).build());
    }

    private void save() {
        FarmRegionEditPacket.sendToServer(FarmRegionEditPacket.ACTION_UPDATE, maidUuid, index,
                region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ(),
                cropBox.getValue().trim(), rightHarvest ? "right" : "left", nameBox.getValue().trim(),
                region.seedX() == null ? FarmRegionStorage.NO_BOX : region.seedX(),
                region.seedY() == null ? FarmRegionStorage.NO_BOX : region.seedY(),
                region.seedZ() == null ? FarmRegionStorage.NO_BOX : region.seedZ(),
                region.harvestX() == null ? FarmRegionStorage.NO_BOX : region.harvestX(),
                region.harvestY() == null ? FarmRegionStorage.NO_BOX : region.harvestY(),
                region.harvestZ() == null ? FarmRegionStorage.NO_BOX : region.harvestZ());
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 下拉框打开时: 点列表外 → 关闭下拉框
        if (dropdownOpen && button == 0) {
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            int listX = px + 90, listY = py + 94;
            int listH = Math.min(112, seeds.size() * 14);
            // 不在列表内 且 不在下拉按钮上 → 关闭
            if (!(mx >= listX && mx < listX + 200 && my >= listY && my < listY + listH)
                    && !(mx >= px + 264 && mx < px + 290 && my >= py + 78 && my < py + 94)) {
                dropdownOpen = false;
            }
        }
        if (dropdownOpen && button == 0) {
            int px = (this.width - PANEL_W) / 2;
            int py = (this.height - PANEL_H) / 2;
            int listX = px + 90, listY = py + 94;
            int listH = Math.min(112, seeds.size() * 14);
            if (mx >= listX && mx < listX + 200 && my >= listY && my < listY + listH) {
                int idx = dropdownScroll + (int) ((my - listY) / 14);
                if (idx >= 0 && idx < seeds.size()) {
                    cropBox.setValue(seeds.get(idx));
                    dropdownOpen = false;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

//? if 1.20.1 {
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (dropdownOpen && seeds.size() > 8) {
            dropdownScroll += delta > 0 ? -1 : 1;
            dropdownScroll = Math.max(0, Math.min(seeds.size() - 1, dropdownScroll));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
//?} else {
    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double delta) {
        if (dropdownOpen && seeds.size() > 8) {
            dropdownScroll += delta > 0 ? -1 : 1;
            dropdownScroll = Math.max(0, Math.min(seeds.size() - 1, dropdownScroll));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, delta);
    }
//?}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        this.renderBackground(g);
//?} else {
        this.renderBackground(g, mx, my, pt);
//?}
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        g.drawCenteredString(this.font, title, this.width / 2, py + 10, COLOR_TEXT);
        g.drawString(this.font, Component.literal("区域名字"), px + 16, py + 33, COLOR_SUB, false);
        g.drawString(this.font, Component.literal("收获方式"), px + 16, py + 57, COLOR_SUB, false);
        g.drawString(this.font, Component.literal("种子 id"), px + 16, py + 81, COLOR_SUB, false);
        g.drawString(this.font, Component.literal("坐标: (" + region.minX() + "," + region.minY() + "," + region.minZ() + ") ~ (" + region.maxX() + "," + region.maxY() + "," + region.maxZ() + ")"), px + 16, py + 128, COLOR_SUB, false);
        g.drawString(this.font, Component.literal("种子箱: " + boxStr(region.seedX(), region.seedY(), region.seedZ())), px + 16, py + 142, COLOR_SUB, false);
        g.drawString(this.font, Component.literal("收获箱: " + boxStr(region.harvestX(), region.harvestY(), region.harvestZ())), px + 16, py + 156, COLOR_SUB, false);
        if (dropdownOpen) {
            int listX = px + 90, listY = py + 94;
            int maxRows = Math.min(8, seeds.size());
            g.fill(listX, listY, listX + 200, listY + maxRows * 14, 0xFF1A1A1A);
            for (int i = 0; i < maxRows; i++) {
                int idx = dropdownScroll + i;
                if (idx >= seeds.size()) break;
                boolean hov = mx >= listX && mx < listX + 200 && my >= listY + i * 14 && my < listY + i * 14 + 14;
                if (hov) g.fill(listX, listY + i * 14, listX + 200, listY + i * 14 + 14, COLOR_SEL);
                g.drawString(this.font, Component.literal(seeds.get(idx)), listX + 4, listY + i * 14 + 3, 0xFFFFFF, false);
            }
        }
        super.render(g, mx, my, pt);
    }

    private static String boxStr(Integer x, Integer y, Integer z) {
        return x == null ? "未绑定" : "(" + x + "," + y + "," + z + ")";
    }

    /** GUI 规范 (错题集 gui.md §2): 覆写 renderBackground 用透明背景 — 1.21 默认 0xC0 渐变压黑 → 字模糊 */
    @Override
//? if 1.20.1 {
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x40101010);
    }
//?} else {
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x40101010);
    }
//?}

    @Override
    public boolean isPauseScreen() { return false; }
}
