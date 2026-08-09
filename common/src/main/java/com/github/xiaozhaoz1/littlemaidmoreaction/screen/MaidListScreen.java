package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidListQueryPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidListResponsePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.ChatFormatting;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidListResponsePacket.MaidEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * v79.25: 女仆独立选择屏 — 独立 Screen (非 TLM 256×256 容器屏)。入口: LMAConfigScreen "女仆选择" 按钮。
 * <p>v79.25.2: 数据源 = 服务端全维度扫描 (C2S {@link MaidListQueryPacket} → S2C
 * {@link MaidListResponsePacket} 静态缓存轮询) + TLM 棕色主面板背景。
 * <p><b>v79.26 重设计</b> (回归 v79.25 大面板 320×240, 256×256 太小太挤):
 * 宽列表 (170) 行高 22 双行信息 (名称 / 等级 + ❤ + 距离), 右侧大 3D 预览 (50 缩放)。
 * <b>半身修复 (错题 #137)</b>: 1.21 的 10 参 renderEntityInInventoryFollowsMouse 是
 * <b>区域+缩放+yOffset</b> 语义 (x1,y1,x2,y2 — TLM 自家 67×112 区域), 旧代码传
 * px+45,py+45 = 45×45 小框 → 下半身被框裁掉只剩半身; 1.20 的 7 参是中心点+缩放 —
 * 照抄参数位置即错 (错题 #136 同族: 双平台方法语义差异)。
 * <p><b>v79.26.2 背景改原版主菜单旋转全景</b> (= 主菜单 PanoramaRenderer 全景 cube map,
 * 非 TLM 棕面板/原版容器风): {@link PanoramaRenderer} 类双平台均在 <b>net.minecraft.client.renderer</b>
 * 包 (本地 decompile 实证 — 不在 gui.screens), 构造收 {@link CubeMap} (MainMenuScreen 同款);
 * 渲染不调 super.renderBackground — 1.21 默认含 renderBlurredBackground 背景模糊,
 * 明确去模糊 (让字不模糊) — 全景本身全屏无模糊。
 * 1.21.1 用 Screen 内置 renderPanorama (v79.25 LMAConfigScreen 同款); 1.20.1 无
 * renderPanorama → PanoramaRenderer 直渲兜底。
 * <p><b>v79.26.3 去棕面板</b> (实测: 棕面板 + 左右深棕补条不要, 全屏只留全景背景,
 * 列表/预览/按钮直接浮全景上): 删 MaidPanelStyle 棕面板 blit, 背景统一走
 * {@link PanoramaBackground} (全 LMA 屏同款); 3D 预览保留
 * renderEntityInInventoryFollowsMouse (原版物品栏同款拖拽旋转展示)。</p>
 */
public final class MaidListScreen extends Screen {

    /** v79.26 回归: 320×240 大面板 (v79.25 尺寸) */
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 240;
    private static final int LIST_X = 16;
    private static final int LIST_Y = 32;
    private static final int LIST_W = 170;
    private static final int LIST_H = 190;
    private static final int ROW_H = 22;
    /** 右侧预览区 (宽 320-16-200=104) — 1.21 区域版高 150 (完整女仆) */
    private static final int PREVIEW_X = 200;
    private static final int PREVIEW_Y = 36;
    private static final int PREVIEW_W = 104;
    private static final int PREVIEW_H = 150;
    /** 本地实体探测范围 (仅邻近女仆有实体引用可进属性屏; 列表本身全维度) */
    private static final int LOCAL_PROBE_RANGE = 512;
    /** 本地探测 20t 节流 (512 格 box 遍历有开销, 不必每 tick) */
    private static final int PROBE_INTERVAL = 20;

    /** 深棕底浅米字配色 (回归 v79.26.1 TLM 棕面板配色) */
    private static final int COLOR_TEXT = 0xFFFFE8D9;
    private static final int COLOR_SUB = 0xFFC0B0A0;
    private static final int COLOR_GOLD = 0xFFE8B74A;
    private static final int COLOR_HEART = 0xFFE85B5B;
    private static final int COLOR_SELECTED = 0x55E8D9B8;
    private static final int COLOR_HOVER = 0x44FFFFFF;

    private final Screen parent;
    private final List<MaidEntry> entries = new ArrayList<>();
    private int selected;
    private int scroll;
    private int probeCounter;
    private Button attrButton;

    public MaidListScreen(Screen parent) {
        super(Component.translatable("screen.littlemaidmoreaction.maid_list"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        // v79.25.2: 打开即请求服务端全维度扫描 (C2S)
        MaidListQueryPacket.sendToServer();
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.back"),
                btn -> Minecraft.getInstance().setScreen(parent))
                .pos(px + LIST_X, py + PANEL_H - 26).size(80, 20).build());
        this.attrButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.littlemaidmoreaction.maid_list.open_attr"),
                btn -> openAttribute())
                .pos(px + PREVIEW_X + 6, py + PANEL_H - 26).size(92, 20).build());
        this.attrButton.active = false;
    }

    @Override
    public void tick() {
        // v79.25.2: 轮询服务端响应缓存 (S2C handle 落 MaidListResponsePacket 静态缓存)
        List<MaidEntry> fresh = MaidListResponsePacket.getLastEntries();
        if (!fresh.equals(entries)) {
            entries.clear();
            entries.addAll(fresh);
            if (selected >= entries.size()) {
                selected = entries.isEmpty() ? 0 : entries.size() - 1;
            }
            clampScroll();
        }
        // 20t 节流探测本地实体: 属性按钮仅本地实体存在时可点 (远端女仆属性屏读不了)
        if (++probeCounter >= PROBE_INTERVAL) {
            probeCounter = 0;
            this.attrButton.active = findLocal(selectedMaidId()) != null;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        g.drawCenteredString(font, title, this.width / 2, py + 12, COLOR_TEXT);
        if (entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable(
                    "screen.littlemaidmoreaction.maid_list.empty"), this.width / 2, py + 120, COLOR_SUB);
        } else {
            drawList(g, px + LIST_X, py + LIST_Y, mx, my);
            drawPreview(g, px + PREVIEW_X, py + PREVIEW_Y, mx, my);
        }
        super.render(g, mx, my, pt);
    }

    /**
     * v79.26.3: 原版主菜单旋转全景背景 (统一 {@link PanoramaBackground})。不调 super:
     * 1.21 默认 renderBackground 含 renderBlurredBackground (背景模糊, 明确去模糊
     * — 让字不模糊)。1.21.1 用 Screen 内置 renderPanorama (v79.25 LMAConfigScreen
     * 同款, 原版全景+vignette); 1.20.1 无此方法 → {@link PanoramaBackground} 直渲兜底
     * (PanoramaRenderer, partialTick 传 1.0F, 全景旋转基于实时时钟, 插值仅影响平滑度)。
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

    private void drawList(GuiGraphics g, int lx, int ly, int mx, int my) {
        g.enableScissor(lx, ly, lx + LIST_W, ly + LIST_H);
        for (int i = scroll; i < entries.size(); i++) {
            int rowY = ly + (i - scroll) * ROW_H;
            if (rowY + ROW_H > ly + LIST_H) {
                break;
            }
            MaidEntry e = entries.get(i);
            boolean hovered = mx >= lx && mx < lx + LIST_W && my >= rowY && my < rowY + ROW_H;
            if (i == selected) {
                g.fill(lx, rowY, lx + LIST_W, rowY + ROW_H, COLOR_SELECTED);
            }
            if (hovered) {
                g.fill(lx, rowY, lx + LIST_W, rowY + ROW_H, COLOR_HOVER);
            }
            // v79.26 双行: 名称 (行1) + 等级 ❤ 距离 (行2) — v79.26.1 等级改 lang key (中文)
            g.drawString(font, e.name(), lx + 5, rowY + 2, COLOR_TEXT, false);
            Component lv = Component.translatable(
                    "gui.littlemaidmoreaction.maid_list.level", e.level()).withStyle(ChatFormatting.GOLD);
            Component heart = Component.literal("❤ " + (int) e.health() + "/" + (int) e.maxHealth())
                    .withStyle(ChatFormatting.RED);
            String dist = Math.round(Math.sqrt(e.distSqr())) + "格";
            Component distC = Component.literal(dist).withStyle(ChatFormatting.GRAY);
            // 行2 三段: 等级 左, ❤ 中, 距离右对齐
            g.drawString(font, lv, lx + 5, rowY + 13, COLOR_GOLD, false);
            g.drawString(font, heart, lx + 52, rowY + 13, COLOR_HEART, false);
            g.drawString(font, distC, lx + LIST_W - 5 - font.width(distC), rowY + 13, COLOR_SUB, false);
        }
        g.disableScissor();
    }

    private void drawPreview(GuiGraphics g, int px, int py, int mx, int my) {
        MaidEntry e = entries.get(selected);
        EntityMaid local = findLocal(e.uuid());
        if (local != null) {
            // 半身修复 (错题 #137): 1.21 区域版给足区域 (104×150), 实体完整显示
            //? if 1.20.1 {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, py + PREVIEW_H / 2, 50,
                    (float) (px - mx), (float) (py - my), local);
            //?} else {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, py, px + PREVIEW_W, py + PREVIEW_H, 50, 0.0F,
                    (float) (px - mx), (float) (py - my), local);
            //?}
            drawCentered(g, local.getName(), px + PREVIEW_W / 2, py + PREVIEW_H + 6, COLOR_TEXT);
            Component lv = Component.translatable(
                    "gui.littlemaidmoreaction.maid_list.level", e.level()).withStyle(ChatFormatting.GOLD);
            Component heart = Component.literal(" ❤ " + (int) e.health() + "/" + (int) e.maxHealth())
                    .withStyle(ChatFormatting.RED);
            drawCentered(g, lv.copy().append(heart), px + PREVIEW_W / 2, py + PREVIEW_H + 20, COLOR_TEXT);
        } else {
            // 远端女仆 (跨维度/远距离): 无实体引用 — 只显示信息, 属性按钮已禁用
            drawCentered(g, Component.literal(e.name()), px + PREVIEW_W / 2, py + 20, COLOR_TEXT);
            drawCentered(g, Component.literal(e.dimension()), px + PREVIEW_W / 2, py + 40, COLOR_SUB);
            drawCentered(g, Component.translatable(
                    "screen.littlemaidmoreaction.maid_list.not_near"), px + PREVIEW_W / 2, py + 62, COLOR_SUB);
        }
    }

    /** drawCenteredString 统一 5 参 — 双平台均无 boolean shadow 版 (编译实证, 错题 #136) */
    private void drawCentered(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawCenteredString(font, text, x, y, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 点行选中 (不直接开属性屏 — v79.25.2 点选 + 按钮进入, 防误触)
        if (button == 0 && !entries.isEmpty() && isListArea(mx, my)) {
            int index = scroll + (int) ((my - this.getListTop() - LIST_Y) / ROW_H);
            if (index >= 0 && index < entries.size()) {
                selected = index;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

//? if 1.20.1 {
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (isListArea(mx, my)) {
            scroll += delta > 0 ? -1 : 1;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
//?} else {
    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double delta) {
        if (isListArea(mx, my)) {
            scroll += delta > 0 ? -1 : 1;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, delta);
    }
//?}

    private int getListTop() {
        return (this.height - PANEL_H) / 2;
    }

    private boolean isListArea(double mx, double my) {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        return mx >= px + LIST_X && mx < px + LIST_X + LIST_W
                && my >= py + LIST_Y && my < py + LIST_Y + LIST_H;
    }

    private UUID selectedMaidId() {
        return entries.isEmpty() ? null : entries.get(selected).uuid();
    }

    /**
     * 客户端当前维度找本地实体 (3D 预览 + 属性屏需要 — 仅邻近女仆有实体引用, 远处/跨维度女仆
     * 属性按钮禁用)。用 getEntitiesOfClass box 探测 (双平台通用 — 1.20.1 ClientLevel 无无参
     * getEntities())。错题 #136: LmaCommand 的 ServerLevel.getEntities().getAll() 不能外推到
     * ClientLevel — 平台差异必须逐调用验证。
     */
    private EntityMaid findLocal(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }
        AABB box = new AABB(mc.player.blockPosition()).inflate(LOCAL_PROBE_RANGE);
        for (EntityMaid m : mc.level.getEntitiesOfClass(EntityMaid.class, box,
                m -> m.getUUID().equals(uuid))) {
            return m;
        }
        return null;
    }

    private void openAttribute() {
        EntityMaid local = findLocal(selectedMaidId());
        if (local != null) {
            Minecraft.getInstance().setScreen(new MaidAttributeScreen(this, local));
        }
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, entries.size() - LIST_H / ROW_H);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }
}
