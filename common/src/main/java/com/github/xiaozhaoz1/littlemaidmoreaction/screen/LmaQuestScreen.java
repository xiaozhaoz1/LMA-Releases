package com.github.xiaozhaoz1.littlemaidmoreaction.screen;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.quest.QuestNode;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.quest.QuestTreeLayout;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.quest.QuestTreeLoader;
//? if 1.20.1 {
import net.minecraft.advancements.Advancement;
//?} else {
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
//?}
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 成就树 UI (M1) — 复刻 FTB Quests 风格, 数据 = 原版成就, 自动布局, 零新依赖 (原版 Screen)。
 *
 * <p>三层: 左栏章节列表 / 中间任务树画布 (自动布局 + 拖拽平移) / 右栏任务详情。
 * 进度读取: 实现 {@link ClientAdvancements.Listener} + setListener — 原版 AdvancementsScreen 同款
 * (1.21.1 ClientAdvancements.setListener 会把已有 progress 全推给 listener, 源码实证)。
 *
 * <p>数据来源: 运行时读数据包原版成就 JSON (QuestTreeLoader), 自动跟随版本 (用户裁定)。
 * 章节 = 根节点 (story/adventure/nether/end/husbandry), 章节内 = 子树自动布局。
 */
public final class LmaQuestScreen extends Screen implements ClientAdvancements.Listener {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("LMA/QuestScreen");

    private static final int LEFT_W = 150;        // 左栏章节展开宽度
    private static final int LEFT_W_COLLAPSED = 28; // 左栏收起宽度 (只留箭头)
    private static final int RIGHT_W = 220;       // 右栏详情宽度
    private static final int TOP_H = 28;          // 顶部标题高度
    private static final int NODE_SIZE = 26;      // 节点尺寸 (对齐原版)

    private final Screen parent;
    private final List<QuestNode> roots = new ArrayList<>();   // 所有章节根
    private final Map<String, QuestNode> byId = new HashMap<>();
    private final Map<String, QuestTreeLayout.Pos> layout = new HashMap<>();
    private final Map<ResourceLocation, AdvancementProgress> progress = new HashMap<>();

    @Nullable private QuestNode selectedChapter;  // 当前章节 (根)
    @Nullable private QuestNode hovered;           // 悬停节点
    @Nullable private QuestNode selected;          // 选中节点 (详情)
    private double scrollX, scrollY;               // 画布平移
    private int scrollChapter = 0;                 // 左栏章节滚动偏移
    private boolean leftCollapsed = false;         // 左栏是否收起 (点箭头)
    private boolean detailOpen = false;            // 右侧详情是否展开 (点任务才开)
    private boolean dragging;
    private int dragLastX, dragLastY;

    public LmaQuestScreen(Screen parent) {
        super(Component.literal("LMA 成就树"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // ★ v79.63.13 (用户反馈「成就树没有返回按钮」): 底部居中 80×20 返回键 ✓
        //   坐标: x = (width-80)/2 · y = height-26 · 尺寸 80×20 (Esc 关闭仍然可用 ✓)
        //   用原版键 gui.back (中文"返回"/英文 "Back" ✓ 无需新语言键) + tooltip ✓
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        net.minecraft.network.chat.Component.translatable("gui.back"),
                        b -> this.onClose())
                .pos((this.width - 80) / 2, this.height - 26).size(80, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.quest.back.tip")))
                .build());
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.getAdvancements().setListener(this);
        reloadTree();
        if (selectedChapter == null && !roots.isEmpty()) selectedChapter = roots.get(0);
        layoutChapter();
    }

    /**
     * 从客户端成就树构建 QuestNode 树 (数据来自网络同步, 非 ResourceManager —
     * 客户端成就 JSON 不在本地资源, 实证: 日志 "Loaded 2636" 是数据包侧)。
     * 双平台: 1.21.1 AdvancementTree.roots() / 1.20.1 AdvancementList.
     */
    private void reloadTree() {
        roots.clear(); byId.clear(); layout.clear();
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            LOGGER.warn("[LMA/Quest] 连接为空, 无法读成就树");
            return;
        }
        List<QuestNode> loaded = new ArrayList<>();
//? if 1.20.1 {
        // 1.20.1: getAllAdvancements() -> Collection<Advancement> (全部, 含子 — 只遍历根则子永远缺失)
        Collection<net.minecraft.advancements.Advancement> all =
                conn.getAdvancements().getAdvancements().getAllAdvancements();
        for (net.minecraft.advancements.Advancement adv : all) {
            QuestNode n = fromAdvancement(adv);
            if (n != null) loaded.add(n);
        }
//?} else {
        // 1.21.1: getTree().nodes() -> Collection<AdvancementNode> (全部, 含子)
        Collection<net.minecraft.advancements.AdvancementNode> allNodes =
                conn.getAdvancements().getTree().nodes();
        for (net.minecraft.advancements.AdvancementNode node : allNodes) {
            QuestNode n = fromNode(node);
            if (n != null) loaded.add(n);
        }
//?}
        // 构建父子关系 (依赖 parent 字段)
        Map<String, QuestNode> byId = new HashMap<>();
        for (QuestNode n : loaded) byId.put(n.id(), n);
        List<QuestNode> rootsList = new ArrayList<>();
        for (QuestNode n : loaded) {
            if (n.parentId() == null) rootsList.add(n);
            else {
                QuestNode p = byId.get(n.parentId());
                if (p != null) p.children().add(n);
                else rootsList.add(n);
            }
        }
        this.roots.addAll(rootsList);
        this.byId.putAll(byId);
        indexNodes(rootsList);
        LOGGER.info("[LMA/Quest] 成就树: 全部节点={} 根(章节)={}",
                this.byId.size(), this.roots.size());
        if (this.roots.isEmpty()) {
            LOGGER.warn("[LMA/Quest] 无章节! 原始节点={}, conn={}",
                    loaded.size(), conn.getAdvancements() != null
                            ? conn.getAdvancements().getClass().getSimpleName() : "null");
        }
    }

    /** 1.21.1: AdvancementNode → QuestNode */
//? if 1.20.1 {
//?} else {
    private QuestNode fromNode(net.minecraft.advancements.AdvancementNode node) {
        net.minecraft.advancements.Advancement adv = node.advancement();
        java.util.Optional<net.minecraft.advancements.DisplayInfo> disp = adv.display();
        if (disp.isEmpty()) return null;   // 隐藏成就 (无 display)
        net.minecraft.advancements.DisplayInfo d = disp.get();
        return new QuestNode(
                node.holder().id().toString(),
                node.parent() != null ? node.parent().holder().id().toString() : null,
                d.getTitle() != null ? d.getTitle().getString() : null,
                d.getDescription() != null ? d.getDescription().getString() : null,
                d.getIcon() != null && !d.getIcon().isEmpty() ? d.getIcon().getItem().toString() : null,
                d.getType() != null ? d.getType().getSerializedName() : "task",
                d.getBackground().map(rl -> rl.toString()).orElse(null));
    }
//?}

    /** 1.20.1: Advancement → QuestNode */
//? if 1.20.1 {
    private QuestNode fromAdvancement(net.minecraft.advancements.Advancement adv) {
        net.minecraft.advancements.DisplayInfo d = adv.getDisplay();
        if (d == null) return null;   // 隐藏成就
        return new QuestNode(
                adv.getId().toString(),
                adv.getParent() != null ? adv.getParent().toString() : null,
                d.getTitle() != null ? d.getTitle().getString() : null,
                d.getDescription() != null ? d.getDescription().getString() : null,
                d.getIcon() != null && !d.getIcon().isEmpty() ? d.getIcon().getItem().toString() : null,
                d.getFrame() != null ? d.getFrame().getName() : "task",
                d.getBackground() != null ? d.getBackground().toString() : null);
    }
//?}

    private void indexNodes(List<QuestNode> nodes) {
        for (QuestNode n : nodes) {
            byId.put(n.id(), n);
            indexNodes(n.children());
        }
    }

    /** 当前章节布局 (只排选中章节的子树) */
    private void layoutChapter() {
        layout.clear();
        if (selectedChapter != null) {
            // 章节 = 根, 布局其子树 (根在顶层, 子树展开)
            layout.putAll(QuestTreeLayout.layout(List.of(selectedChapter)));
        }
    }

    /** 背景 — 原版全景 (去模糊, 错题 #138/v75.2: 1.21 默认 renderBackground 含 renderBlurredBackground 模糊) */
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

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
//? if 1.20.1 {
        renderBackground(g);
//?} else {
        renderBackground(g, mx, my, pt);
//?}
        // 懒加载: 若成就树为空 (网络同步晚到), 每帧重试
        if (roots.isEmpty() && Minecraft.getInstance().getConnection() != null) {
            reloadTree();
            if (selectedChapter == null && !roots.isEmpty()) selectedChapter = roots.get(0);
            layoutChapter();
        }

        // 动态布局: 左栏收起 + 详情收起时画布全宽
        int leftW = leftCollapsed ? LEFT_W_COLLAPSED : LEFT_W;
        int rightW = detailOpen ? RIGHT_W : 0;
        int canvasX = leftW;
        int canvasY = TOP_H;
        int canvasW = this.width - leftW - rightW;
        int canvasH = this.height - TOP_H;

        // ── 左栏章节 (可收起 + 可滚动) ──
        g.fill(0, 0, leftW, this.height, 0xAA141414);
        if (leftCollapsed) {
            // 收起态: 只留展开箭头 ▶
            g.drawCenteredString(font, Component.literal("▶").withStyle(s -> s.withColor(0xFFD700)),
                    leftW / 2, this.height / 2, 0xFFFFFF);
        } else {
            g.drawCenteredString(font, Component.literal("章节"), leftW / 2, 8, 0xFFD700);
            // 收起箭头 ◀ (点它收起)
            g.drawString(font, Component.literal("◀").withStyle(s -> s.withColor(0xFFFFFF)),
                    leftW - 16, 8, 0xFFFFFF);
            int ry = TOP_H + 4 - scrollChapter * 20;
            g.enableScissor(2, TOP_H, leftW - 2, this.height - 4);
            for (QuestNode ch : roots) {
                boolean sel = ch == selectedChapter;
                if (sel) g.fill(4, ry, leftW - 4, ry + 18, 0x553355AA);
                String title = ch.titleKey() != null ? net.minecraft.client.resources.language.I18n.get(ch.titleKey()) : ch.id();
                g.drawString(font, Component.literal((sel ? "▶ " : "  ") + title).withStyle(s -> s.withColor(sel ? 0x55FF55 : 0xCCCCCC)),
                        8, ry + 4, 0xFFFFFF);
                ry += 20;
            }
            g.disableScissor();
        }

        // ── 中间画布 (滚轮平移) ──
        g.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xAA000000);
        g.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
        int ox = canvasX + (int) scrollX;
        int oy = canvasY + (int) scrollY;
        // 连线 (父子)
        if (selectedChapter != null) drawConnections(g, selectedChapter, ox, oy);
        // 节点
        if (selectedChapter != null) drawNode(g, selectedChapter, ox, oy, mx, my);
        g.disableScissor();

        // ── 右栏详情 (点任务才开, 可收回) ──
        if (detailOpen && selected != null) {
            int dx = this.width - RIGHT_W;
            g.fill(dx, 0, this.width, this.height, 0xAA141414);
            // 收回箭头 ✕
            g.drawString(font, Component.literal("✕").withStyle(s -> s.withColor(0xFFFFFF)),
                    dx + RIGHT_W - 16, 8, 0xFFFFFF);
            g.drawCenteredString(font, Component.literal("任务详情").withStyle(s -> s.withColor(0xFFD700)), dx + RIGHT_W / 2, 8, 0xFFFFFF);
            String title = selected.titleKey() != null ? net.minecraft.client.resources.language.I18n.get(selected.titleKey()) : selected.id();
            g.drawString(font, Component.literal(title).withStyle(s -> s.withColor(0x55FF55)), dx + 8, 28, 0xFFFFFF);
            String desc = selected.descriptionKey() != null ? net.minecraft.client.resources.language.I18n.get(selected.descriptionKey()) : "";
            g.drawWordWrap(font, Component.literal(desc).withStyle(s -> s.withColor(0xCCCCCC)), dx + 8, 46, RIGHT_W - 16, 0xFFFFFF);
            // 进度
            AdvancementProgress p = progress.get(rl(selected.id()));
            float pct = p == null ? 0f : p.getPercent();
            g.drawString(font, Component.literal("进度: " + (int)(pct * 100) + "%").withStyle(s -> s.withColor(0xFFD700)),
                    dx + 8, 46 + 60, 0xFFFFFF);
        }

        // 顶部标题
        g.drawCenteredString(font, Component.literal("LMA 成就树 (" + roots.size() + " 章)").withStyle(s -> s.withColor(0xFFD700)),
                this.width / 2, 6, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    private void drawConnections(GuiGraphics g, QuestNode node, int ox, int oy) {
        QuestTreeLayout.Pos pos = layout.get(node.id());
        if (pos == null) return;
        for (QuestNode child : node.children()) {
            QuestTreeLayout.Pos cpos = layout.get(child.id());
            if (cpos != null) {
                int x1 = ox + pos.x() + NODE_SIZE / 2, y1 = oy + pos.y() + NODE_SIZE / 2;
                int x2 = ox + cpos.x() + NODE_SIZE / 2, y2 = oy + cpos.y() + NODE_SIZE / 2;
                g.hLine(x1, x2, y1, 0xFF888888);
                g.vLine(x2, y1, y2, 0xFF888888);
            }
            drawConnections(g, child, ox, oy);
        }
    }

    private void drawNode(GuiGraphics g, QuestNode node, int ox, int oy, int mx, int my) {
        QuestTreeLayout.Pos pos = layout.get(node.id());
        if (pos == null) return;
        int x = ox + pos.x(), y = oy + pos.y();
        // 状态色: 完成金 / 锁定灰 / 可用白
        boolean done = isDone(node);
        boolean locked = isLocked(node);
        int color = done ? 0xFFD700 : (locked ? 0x666666 : 0xFFFFFF);
        g.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, 0xAA000000);
        g.renderOutline(x, y, NODE_SIZE, NODE_SIZE, color);
        // 画图标 (物品图标 — 从 iconId 解析 ItemStack)
        if (node.iconId() != null) {
            net.minecraft.world.item.ItemStack stack = itemFromId(node.iconId());
            if (!stack.isEmpty()) {
                g.renderFakeItem(stack, x + 5, y + 5);
            } else {
                g.fill(x + 4, y + 4, x + NODE_SIZE - 4, y + NODE_SIZE - 4, 0x553355AA);
            }
        } else {
            g.fill(x + 4, y + 4, x + NODE_SIZE - 4, y + NODE_SIZE - 4, 0x553355AA);
        }
        // 悬停检测
        if (mx >= x && mx <= x + NODE_SIZE && my >= y && my <= y + NODE_SIZE) {
            hovered = node;
            g.renderOutline(x - 1, y - 1, NODE_SIZE + 2, NODE_SIZE + 2, 0xFFFFFF);
        }
        for (QuestNode child : node.children()) drawNode(g, child, ox, oy, mx, my);
    }

    /** 从 iconId 解析物品 (如 "minecraft:stone") — 双平台注册表 */
    private static net.minecraft.world.item.ItemStack itemFromId(String id) {
        try {
            net.minecraft.resources.ResourceLocation rl = rl(id);
//? if 1.20.1 {
            return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl));
//?} else {
            return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl));
//?}
        } catch (Exception e) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
    }

    private boolean isDone(QuestNode node) {
        AdvancementProgress p = progress.get(rl(node.id()));
        return p != null && p.isDone();
    }

    private boolean isLocked(QuestNode node) {
        if (node.parentId() == null) return false;
        QuestNode parent = byId.get(node.parentId());
        return parent != null && !isDone(parent);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 当前左栏宽 (收起/展开)
        int leftW = leftCollapsed ? LEFT_W_COLLAPSED : LEFT_W;
        // 左栏收起箭头 (展开态点 ◀ → 收起)
        if (mx < leftW && my < TOP_H && !leftCollapsed) {
            leftCollapsed = true;
            return true;
        }
        // 左栏展开箭头 (收起态点 ▶ → 展开) — 收起态只响应这个, 不响应章节点击
        if (mx < LEFT_W_COLLAPSED && leftCollapsed) {
            leftCollapsed = false;
            return true;
        }
        // 左栏章节选择 — 仅展开态 (收起态不换章节)
        if (!leftCollapsed && mx < leftW && my > TOP_H) {
            int idx = (int) ((my - TOP_H - 4) / 20) + scrollChapter;
            if (idx >= 0 && idx < roots.size()) {
                selectedChapter = roots.get(idx);
                selected = null; scrollX = scrollY = 0; layoutChapter();
                return true;
            }
        }
        // 右栏收回箭头 (详情 ✕)
        if (detailOpen && mx >= this.width - RIGHT_W && my < TOP_H) {
            detailOpen = false; selected = null;
            return true;
        }
        // 节点选中 (画布内, 动态区域)
        int rightW = detailOpen ? RIGHT_W : 0;
        if (mx >= leftW && mx <= this.width - rightW) {
            hovered = null;
            if (selectedChapter != null) {
                QuestNode hit = findNodeAt(selectedChapter, (int)mx - leftW - (int)scrollX, (int)my - TOP_H - (int)scrollY);
                if (hit != null) {
                    selected = hit; detailOpen = true;   // 点任务 → 开详情
                    return true;
                }
            }
        }
        // 拖拽起点 (只在画布内)
        if (mx >= leftW && mx <= this.width - rightW && my >= TOP_H) {
            dragging = true; dragLastX = (int) mx; dragLastY = (int) my;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Nullable
    private QuestNode findNodeAt(QuestNode node, int lx, int ly) {
        QuestTreeLayout.Pos pos = layout.get(node.id());
        if (pos != null && lx >= pos.x() && lx <= pos.x() + NODE_SIZE && ly >= pos.y() && ly <= pos.y() + NODE_SIZE) {
            return node;
        }
        for (QuestNode c : node.children()) {
            QuestNode hit = findNodeAt(c, lx, ly);
            if (hit != null) return hit;
        }
        return null;
    }

    @Override
//? if 1.20.1 {
    public boolean mouseScrolled(double mx, double my, double yDelta) {
        return handleScroll(mx, my, yDelta);
    }
//?} else {
    public boolean mouseScrolled(double mx, double my, double xDelta, double yDelta) {
        return handleScroll(mx, my, xDelta + yDelta);
    }
//?}

    /** 滚轮分流: 左栏=章节滚动 / 画布=平移 (无缩放) */
    private boolean handleScroll(double mx, double my, double delta) {
        int leftW = leftCollapsed ? LEFT_W_COLLAPSED : LEFT_W;
        int rightW = detailOpen ? RIGHT_W : 0;
        // 左栏: 章节滚动 (仅展开态)
        if (!leftCollapsed && mx < leftW && my > TOP_H) {
            scrollChapter = (int) Math.max(0, Math.min(
                    Math.max(0, roots.size() - (this.height - TOP_H) / 20),
                    scrollChapter - (int) delta));
            return true;
        }
        // 画布: 平移 (滚轮垂直/水平)
        if (mx >= leftW && mx <= this.width - rightW && my >= TOP_H) {
            scrollY += delta * 8;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging) {
            scrollX += mx - dragLastX; scrollY += my - dragLastY;
            dragLastX = (int) mx; dragLastY = (int) my;
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = false;
        return super.mouseReleased(mx, my, btn);
    }

    /** ResourceLocation 构造 — 1.20.1 new / 1.21.1 parse (双平台兼容) */
    private static ResourceLocation rl(String id) {
//? if 1.20.1 {
        return new ResourceLocation(id);
//?} else {
        return ResourceLocation.parse(id);
//?}
    }

    @Override
    public void onClose() {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.getAdvancements().setListener(null);
        Minecraft.getInstance().setScreen(parent);
    }

    // ── ClientAdvancements.Listener (双平台: 1.20.1 Advancement / 1.21.1 AdvancementNode) ──

//? if 1.20.1 {
    @Override
    public void onAddAdvancementRoot(net.minecraft.advancements.Advancement a) {
        if (roots.isEmpty()) { reloadTree(); if (selectedChapter == null && !roots.isEmpty()) selectedChapter = roots.get(0); layoutChapter(); }
    }

    @Override
    public void onRemoveAdvancementRoot(net.minecraft.advancements.Advancement a) {}

    @Override
    public void onAddAdvancementTask(net.minecraft.advancements.Advancement a) {
        if (roots.isEmpty()) { reloadTree(); if (selectedChapter == null && !roots.isEmpty()) selectedChapter = roots.get(0); layoutChapter(); }
    }

    @Override
    public void onRemoveAdvancementTask(net.minecraft.advancements.Advancement a) {}

    @Override
    public void onUpdateAdvancementProgress(net.minecraft.advancements.Advancement a, AdvancementProgress p) {
        progress.put(a.getId(), p);
    }

    @Override
    public void onSelectedTabChanged(@Nullable net.minecraft.advancements.Advancement a) {}

    @Override
    public void onAdvancementsCleared() {
        progress.clear();
    }
//?} else {
    @Override
    public void onAddAdvancementRoot(AdvancementNode node) {
        if (roots.isEmpty()) { reloadTree(); if (selectedChapter == null && !roots.isEmpty()) selectedChapter = roots.get(0); layoutChapter(); }
    }

    @Override
    public void onRemoveAdvancementRoot(AdvancementNode node) {}

    @Override
    public void onAddAdvancementTask(AdvancementNode node) {
        if (roots.isEmpty()) { reloadTree(); if (selectedChapter == null && !roots.isEmpty()) selectedChapter = roots.get(0); layoutChapter(); }
    }

    @Override
    public void onRemoveAdvancementTask(AdvancementNode node) {}

    @Override
    public void onUpdateAdvancementProgress(AdvancementNode node, AdvancementProgress p) {
        progress.put(node.holder().id(), p);
    }

    @Override
    public void onSelectedTabChanged(@Nullable AdvancementHolder holder) {}

    @Override
    public void onAdvancementsCleared() {
        progress.clear();
    }
//?}
}
