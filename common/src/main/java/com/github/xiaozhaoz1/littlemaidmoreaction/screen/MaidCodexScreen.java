package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * v79.47: 女仆图鉴界面 — 击杀统计列表 (实体名 + 击杀数)。
 *
 * <p>数据来自 {@code MaidCodexScreenPacket} (服务端合并全部女仆计数);
 * 排序: 击杀数降序; 实体名 = EntityType.getDescriptionId 翻译键。
 * 文案走 lang key (gui.littlemaidmoreaction.maid_codex.*)。
 */
public final class MaidCodexScreen extends Screen {

    /** 击杀条目 (实体 id → 名称 + 计数) */
    private record Entry(String name, int count) {}

    private final List<Entry> entries = new ArrayList<>();

    public MaidCodexScreen(Map<String, Integer> counts) {
        super(Component.translatable("gui.littlemaidmoreaction.maid_codex.title"));
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        for (Map.Entry<String, Integer> e : sorted) {
            entries.add(new Entry(displayNameOf(e.getKey()), e.getValue()));
        }
    }

    /** 实体注册名 → 本地化名称 (双平台注册表条件化; 未知 → 原 id) */
    private static String displayNameOf(String id) {
        try {
            EntityType<?> type;
            //? if 1.20.1 {
            type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                    .getValue(ResourceLocation.tryParse(id));
            //?} else {
            type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .get(ResourceLocation.parse(id));  // 1.21: getValue 删 → get (错题 5)
            //?}
            if (type != null) {
                return type.getDescription().getString();
            }
        } catch (Exception ignored) {
            // 未知实体 → 显示原 id
        }
        return id;
    }

    @Override
    public void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.littlemaidmoreaction.maid_codex.back"),
                b -> this.onClose()).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if 1.20.1 {
        this.renderBackground(g);
        //?} else {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        //?}
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);

        int y = 38;
        int row = 0;
        for (Entry e : entries) {
            if (y > this.height - 40) break;  // 超出屏底裁剪 (MVP 不滚动)
            String line = e.name() + "  ×" + e.count();
            g.drawString(this.font, line, this.width / 2 - 80, y,
                    row % 2 == 0 ? 0xE8D9B8 : 0xC0B0A0);
            y += 14;
            row++;
        }
        if (entries.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.littlemaidmoreaction.maid_codex.empty"),
                    this.width / 2, this.height / 2 - 10, 0xAAAAAA);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * 原版主菜单旋转全景背景 (统一 {@link PanoramaBackground}, v79.26.3 起全 LMA 屏一致)。
     * 不调 super: 1.21 默认 renderBackground 含 renderBlurredBackground (背景模糊, 明确
     * 去模糊 — 让字不模糊)。1.21.1 用 Screen 内置 renderPanorama (LMAConfigScreen 同款);
     * 1.20.1 无此方法 → {@link PanoramaBackground} 直渲兜底 (错题 #138)。
     */
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
