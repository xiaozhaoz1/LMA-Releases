package littlemaidmoreaction.littlemaidmoreaction.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用全屏列表选择器 — v2: 搜索框 + 可选分类过滤。
 *
 * <p>仿 Minecraft {@code LanguageSelectScreen} 模式，替代 {@code CycleButton}
 * 在选项过多（>10个）时体验差的问题。打开后显示全屏可滚动列表，单击即选即关。</p>
 *
 * <p><b>v2 新增</b>: 搜索框始终可见，输入即过滤；分类下拉按需出现（需传 categoryFn）。
 * 两个过滤器 AND 组合，实时生效。</p>
 *
 * <p><b>用法示例（动作选择，带分类）</b></p>
 * <pre>{@code
 * Minecraft.getInstance().setScreen(new SelectionScreen<>(
 *     this, "选择动作类型",
 *     actions,
 *     IAction::displayName,
 *     a -> a.category().name(),
 *     a -> a.category().name(),
 *     selected -> row.resetTo(selected)
 * ));
 * }</pre>
 *
 * <p><b>用法示例（事件选择，无分类）</b></p>
 * <pre>{@code
 * Minecraft.getInstance().setScreen(new SelectionScreen<>(
 *     this, "选择事件",
 *     Arrays.asList(RuleEvent.values()),
 *     RuleEvent::getDisplayName,
 *     selected -> initEvent = selected.getEventId()
 * ));
 * }</pre>
 *
 * <p>ESC / 直接关闭 → 不触发 onSelect，回到父屏幕。</p>
 *
 * @param <T> 选项数据类型（如 RuleEvent, IAction, ICondition）
 *
 * @see net.minecraft.client.gui.screens.LanguageSelectScreen
 */
public final class SelectionScreen<T> extends Screen {
    private final Screen parent;
    private final Consumer<T> onSelect;
    private final List<T> options;
    private final Function<T, String> labelFn;
    private final Function<T, String> subtitleFn;
    private final Function<T, String> categoryFn;
    private EditBox searchBox;
    private CycleButton<String> categoryBtn;
    private OptionList list;

    /**
     * 无副标题便捷构造器（无分类过滤）。
     */
    public SelectionScreen(Screen parent, String title, List<T> options,
                           Function<T, String> labelFn,
                           Consumer<T> onSelect) {
        this(parent, title, options, labelFn, null, null, onSelect);
    }

    /**
     * 带副标题构造器（无分类过滤）。
     */
    public SelectionScreen(Screen parent, String title, List<T> options,
                           Function<T, String> labelFn,
                           Function<T, String> subtitleFn,
                           Consumer<T> onSelect) {
        this(parent, title, options, labelFn, subtitleFn, null, onSelect);
    }

    /**
     * 完整构造器（v2）。
     *
     * @param parent      返回的父屏幕
     * @param title       屏幕标题（顶部居中显示）
     * @param options     选项列表（不可为空）
     * @param labelFn     选项→左侧显示名
     * @param subtitleFn  选项→右侧副标题（可为 null）
     * @param categoryFn  选项→分类标签（可为 null，无分类下拉）
     * @param onSelect    选中回调（不触发则不调用）
     */
    public SelectionScreen(Screen parent, String title, List<T> options,
                           Function<T, String> labelFn,
                           Function<T, String> subtitleFn,
                           Function<T, String> categoryFn,
                           Consumer<T> onSelect) {
        super(Component.literal(title));
        this.parent = parent;
        this.options = options;
        this.labelFn = labelFn;
        this.subtitleFn = subtitleFn;
        this.categoryFn = categoryFn;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        int leftEdge = 14;

        // ── 搜索框（始终可见）──
        int searchW = (categoryFn != null) ? 150 : width - 28;
        searchBox = new EditBox(font, leftEdge, 22, searchW, 16,
                Component.literal("搜索..."));
        searchBox.setResponder(text -> onFilterChanged());
        addRenderableWidget(searchBox);

        // ── 分类下拉（仅当 categoryFn 提供时）──
        if (categoryFn != null) {
            List<String> categories = extractCategories();
            List<String> cycleValues = new ArrayList<>();
            cycleValues.add("全部");
            cycleValues.addAll(categories);

            int btnX = leftEdge + searchW + 4;
            int btnW = width - btnX - 14;
            categoryBtn = CycleButton.<String>builder(s -> Component.literal(s))
                    .withValues(cycleValues)
                    .create(btnX, 20, btnW, 20,
                            Component.literal("分类"),
                            (btn, value) -> onFilterChanged());
            addRenderableWidget(categoryBtn);
        }

        // ── 选项列表 ──
        list = new OptionList(minecraft, width, height, 46, height - 4, 22);
        addRenderableWidget(list);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, getTitle(), width / 2, 9, 0xFFD700);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ── 分类提取 ──

    /** 从 options 动态提取分类，保持首次出现顺序。 */
    private List<String> extractCategories() {
        if (categoryFn == null) return List.of();
        return options.stream()
                .map(categoryFn)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    // ── 过滤逻辑 ──

    private void onFilterChanged() {
        String search = searchBox.getValue().toLowerCase(Locale.ROOT);
        String category = (categoryBtn != null) ? categoryBtn.getValue() : "全部";

        List<T> filtered = options.stream()
                .filter(opt -> passesSearch(opt, search))
                .filter(opt -> passesCategory(opt, category))
                .collect(Collectors.toList());

        list.refresh(filtered);
    }

    private boolean passesSearch(T opt, String searchLower) {
        if (searchLower.isEmpty()) return true;
        String label = labelFn.apply(opt);
        String sub = (subtitleFn != null) ? subtitleFn.apply(opt) : "";
        return (label + " " + sub).toLowerCase(Locale.ROOT).contains(searchLower);
    }

    private boolean passesCategory(T opt, String category) {
        if (categoryFn == null || "全部".equals(category)) return true;
        String optCat = categoryFn.apply(opt);
        return optCat != null && optCat.equals(category);
    }

    // ── 内部列表 ──

    private final class OptionList extends ObjectSelectionList<Row> {
        OptionList(Minecraft mc, int w, int h, int y0, int y1, int itemH) {
            super(mc, w, h, y0, y1, itemH);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
            refresh(options);
        }

        /** 重建列表条目（过滤时调用）。 */
        void refresh(List<T> filtered) {
            clearEntries();
            for (T opt : filtered) {
                this.addEntry(new Row(opt, labelFn.apply(opt),
                        subtitleFn != null ? subtitleFn.apply(opt) : null));
            }
        }

        @Override
        protected int getScrollbarPosition() {
            return width - 6;
        }

        @Override
        public int getRowWidth() {
            return width - 20;
        }
    }

    // ── 行 ──

    private final class Row extends ObjectSelectionList.Entry<Row> {
        private final T value;
        private final String label;
        private final String subtitle;

        Row(T value, String label, String subtitle) {
            this.value = value;
            this.label = label;
            this.subtitle = subtitle;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left,
                           int rowWidth, int rowHeight, int mx, int my,
                           boolean hovered, float pt) {
            int color = hovered ? 0xFFFFD700 : 0xFFFFFF;
            g.drawString(SelectionScreen.this.font, label,
                    left + 4, top + 2, color);
            if (subtitle != null && !subtitle.isEmpty()) {
                int subW = font.width(subtitle);
                g.drawString(SelectionScreen.this.font, subtitle,
                        left + rowWidth - subW - 4, top + 2, 0xFF888888);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            onSelect.accept(value);
            onClose();
            return true;
        }

        @Override
        public Component getNarration() {
            return subtitle != null
                    ? Component.literal(label + " " + subtitle)
                    : Component.literal(label);
        }
    }
}
