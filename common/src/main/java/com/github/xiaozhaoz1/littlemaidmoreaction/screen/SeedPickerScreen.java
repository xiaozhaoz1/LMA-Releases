package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.CropRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * v79.62.1 种子选择器 — 列出可选种子 id (CropRegistry 内置作物种子 + 常见 mod 种子),
 * 顶部搜索框过滤, 点击某行 → 回填到详情屏的种子 id 框.
 *
 * <p>种子表 = 原版 9 类可种作物种子 + 骨粉可催熟 mod 种子 (CropRegistry.registerHandler 扩展).
 */
public final class SeedPickerScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 240;
    private static final int COLOR_TEXT = 0xFFFFE8D9;
    private static final int COLOR_SUB = 0xFFC0B0A0;
    private static final int ROW_H = 20;

    private final Screen parent;
    private final EditBox target;
    private EditBox searchBox;
    private final List<String> allSeeds;
    private String filter = "";
    private final List<Button> seedButtons = new ArrayList<>();

    public SeedPickerScreen(Screen parent, EditBox target) {
        super(Component.translatable("screen.littlemaidmoreaction.seed_picker"));
        this.parent = parent;
        this.target = target;
        this.allSeeds = buildSeedList();
    }

    /**
     * 种子 id 列表 (v79.62.1 动态扫描) — 遍历物品注册表, 收集所有「可种植物」
     * (ItemNameBlockItem 且方块可种: CropBlock/StemBlock/NetherWartBlock/CocoaBlock,
     * 与 FarmExecute.isSeedItem 同判定) — 覆盖原版 + 其他 mod 的种子.
     * 防御点: 注册表未就绪返回空; 去重 + 按 id 排序.
     */
    public static List<String> allSeedIds() { return buildSeedList(); }

    /** 种子 id 列表 (v79.62.1 动态扫描) */
    private static List<String> buildSeedList() {
        List<String> list = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        var registries = net.minecraft.core.registries.BuiltInRegistries.ITEM;
//? if 1.20.1 {
        for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
//?} else {
        for (net.minecraft.world.item.Item item : registries) {
//?}
            if (item == null) continue;
            if (!(item instanceof net.minecraft.world.item.ItemNameBlockItem itb)) continue;
            net.minecraft.world.level.block.Block b = itb.getBlock();
            boolean seed = b instanceof net.minecraft.world.level.block.CropBlock
                    || b instanceof net.minecraft.world.level.block.StemBlock
                    || b instanceof net.minecraft.world.level.block.NetherWartBlock
                    || b instanceof net.minecraft.world.level.block.CocoaBlock;
            if (!seed) continue;
            String id = item.toString();
            if (seen.add(id)) list.add(id);
        }
        list.sort(String::compareTo);
        return list;
    }

    @Override
    protected void init() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        this.searchBox = new EditBox(this.font, px + 16, py + 30, PANEL_W - 32, 16,
                Component.literal("search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.translatable("screen.littlemaidmoreaction.seed_picker.search"));
        this.addRenderableWidget(searchBox);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                btn -> Minecraft.getInstance().setScreen(parent))
                .pos(px + (PANEL_W - 60) / 2, py + PANEL_H - 26).size(60, 20).build());
        rebuildSeeds();
    }

    private void rebuildSeeds() {
        for (Button b : seedButtons) removeWidget(b);
        seedButtons.clear();
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int y = py + 54;
        int bottomLimit = py + PANEL_H - 34;
        String f = filter;
        for (int i = 0; i < allSeeds.size() && y + ROW_H <= bottomLimit; i++) {
            String seed = allSeeds.get(i);
            if (!f.isEmpty() && !seed.contains(f)) continue;
            Button b = Button.builder(Component.literal(seed), btn -> pick(seed))
                    .pos(px + 16, y).size(PANEL_W - 32, 16).build();
            this.addRenderableWidget(b);
            seedButtons.add(b);
            y += ROW_H;
        }
    }

    private void pick(String seed) {
        target.setValue(seed);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void tick() {
        super.tick();
        // 搜索框实时过滤 (EditBox 输入走 charTyped, keyPressed 可能漏)
        if (searchBox != null) {
            String f = searchBox.getValue();
            if (!f.equals(filter)) {
                filter = f;
                rebuildSeeds();
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        super.render(g, mx, my, pt);
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
