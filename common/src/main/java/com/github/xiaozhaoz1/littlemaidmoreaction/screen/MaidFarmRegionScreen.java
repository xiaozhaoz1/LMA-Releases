package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.network.FarmRegionEditPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.FarmRegionSyncPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.FarmRegionStorage.FarmRegion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * v79.62.1 单女仆作物区域管理屏 — 列表只显示概览 (区域名字/种什么/左右手),
 * 每行「详情」按钮进 {@link MaidFarmRegionDetailScreen} 全量编辑 (名字/种子选择器/坐标/左右手/箱子).
 */
public final class MaidFarmRegionScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 240;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_SUB = 0xFFCCCCCC;
    private static final int ROW_H = 22;
    private static int scrollOffset = 0;  // v79.62.1 滚动偏移 (区域>7时)

    private final Screen parent;
    private final String maidUuid;
    private final String maidName;
    private List<FarmRegion> regions = List.of();
    private final List<Button> detailButtons = new ArrayList<>();
    private final List<Button> delButtons = new ArrayList<>();

    public MaidFarmRegionScreen(Screen parent, String maidUuid, String maidName) {
        super(Component.translatable("screen.littlemaidmoreaction.farm_region"));
        this.parent = parent;
        this.maidUuid = maidUuid;
        this.maidName = maidName;
    }

    @Override
    protected void init() {
        FarmRegionEditPacket.sendToServer(FarmRegionEditPacket.ACTION_QUERY, maidUuid, 0,
                0, 0, 0, 0, 0, 0, "", "left", "", 0, 0, 0, 0, 0, 0);
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                btn -> Minecraft.getInstance().setScreen(parent))
                .pos(px + (PANEL_W - 80) / 2, py + PANEL_H - 26).size(80, 20).build());
        // v79.62.1 修复「返回后按钮消失」: 子屏返回会触发本 init() 重初始化 (控件全清),
        // 必须立即重建列表按钮 — 不能只靠 tick sync 变化 (数据没变时 rebuildRows 不触发)
        if (!regions.isEmpty()) {
            rebuildRows();
        }
    }

    @Override
    public void tick() {
        super.tick();
        FarmRegionSyncPacket sync = FarmRegionSyncPacket.getLastSync();
        if (sync != null && maidUuid.equals(sync.maidUuid()) && !sync.regions().equals(regions)) {
            regions = sync.regions();
            rebuildRows();
        }
    }

    // v79.62.1 滚动支持 (区域>7时)
//? if 1.20.1 {
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scrollOffset += delta > 0 ? 1 : -1;
        rebuildRows();
        return true;
    }
//?} else {
    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double delta) {
        scrollOffset += delta > 0 ? 1 : -1;
        rebuildRows();
        return true;
    }
//?}

    private void rebuildRows() {
        for (Button b : detailButtons) removeWidget(b);
        for (Button b : delButtons) removeWidget(b);
        detailButtons.clear();
        delButtons.clear();
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int maxVisible = (PANEL_H - 40 - 34) / ROW_H;
        int total = regions.size();
        // 钳制滚动偏移
        if (scrollOffset > 0) scrollOffset = 0;
        if (scrollOffset < -(total - maxVisible)) scrollOffset = -(total - maxVisible);
        if (total <= maxVisible) scrollOffset = 0;
        int y = py + 40;
        int bottomLimit = py + PANEL_H - 34;
        for (int i = -scrollOffset; i < regions.size(); i++) {
            if (y + ROW_H > bottomLimit) break;
            if (i < 0) continue;
            final int idx = i;
            Button detail = Button.builder(Component.literal("详情"),
                    btn -> openDetail(idx)).pos(px + PANEL_W - 90, y).size(36, 14).build();
            this.addRenderableWidget(detail);
            detailButtons.add(detail);
            Button del = Button.builder(Component.literal("删"),
                    btn -> removeIndex(idx)).pos(px + PANEL_W - 48, y).size(34, 14).build();
            this.addRenderableWidget(del);
            delButtons.add(del);
            y += ROW_H;
        }
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
        if (regions.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable(
                    "screen.littlemaidmoreaction.farm_region.empty"), this.width / 2, py + 120, COLOR_SUB);
        } else {
            int y = py + 40;
            int bottomLimit = py + PANEL_H - 34;
            for (int i = Math.max(0, -scrollOffset); i < regions.size(); i++) {
                if (y + ROW_H > bottomLimit) break;
                FarmRegion r = regions.get(i);
                g.drawString(this.font, Component.literal(r.displayName() + "  " + i),
                        px + 16, y, COLOR_TEXT, false);
                g.drawString(this.font, Component.literal(r.isRightHarvest() ? "右键收" : "左键收"),
                        px + PANEL_W - 120, y, COLOR_SUB, false);
                String crop = r.cropId() == null || r.cropId().isEmpty() ? "只收不种" : r.cropId();
                g.drawString(this.font, Component.literal("种: " + crop),
                        px + 16, y + 11, COLOR_SUB, false);
                // v79.64 维度归属: 区域跟着女仆走, 但记了维度 — 不在当前维度时该区域**暂停** (用户裁定),
                //   必须在屏上可见, 否则玩家会以为"区域坏了" ✗ (取短名: minecraft:overworld → overworld)
                String dim = r.dimension() == null ? "" : r.dimension();
                String dimShort = dim.isEmpty() ? "任意维度" : dim.substring(dim.indexOf(':') + 1);
                boolean otherDim = !dim.isEmpty()
                        && Minecraft.getInstance().level != null
                        && !dim.equals(Minecraft.getInstance().level.dimension().location().toString());
                g.drawString(this.font,
                        Component.literal(otherDim ? "⚠ 其他维度·暂停 (" + dimShort + ")" : dimShort),
                        px + PANEL_W - 120, y + 11, otherDim ? 0xFFAA00 : COLOR_SUB, false);
                y += ROW_H;
            }
        }
        super.render(g, mx, my, pt);
    }

    private void openDetail(int index) {
        if (index < 0 || index >= regions.size()) return;
        Minecraft.getInstance().setScreen(
                new MaidFarmRegionDetailScreen(this, maidUuid, index, regions.get(index)));
    }

    private void removeIndex(int index) {
        FarmRegionEditPacket.sendToServer(FarmRegionEditPacket.ACTION_REMOVE, maidUuid, index,
                0, 0, 0, 0, 0, 0, "", "left", "", 0, 0, 0, 0, 0, 0);
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
