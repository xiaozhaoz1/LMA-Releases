package littlemaidmoreaction.littlemaidmoreaction.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.maideditor.FieldType;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.maideditor.MaidEditorRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/** v34.2: 注册模式 — apply不rebuild，布局修正 */
public final class MaidEditorScreen extends Screen {
    private static final int COLS = 4, ROWS = 3, PER_PAGE = COLS * ROWS;
    private static final int FIELD_W = 80, FIELD_H = 20, GAP_X = 24, GAP_Y = 38;

    private final Screen parent;
    private final EntityMaid maid;
    private final List<String> groupNames;
    private int groupIdx;
    private int pageIdx;
    private final Map<String, String> fieldCache = new LinkedHashMap<>();

    private int colW, startX, startY;
    private final List<AbstractWidget> fieldWidgets = new ArrayList<>();

    public MaidEditorScreen(Screen parent, EntityMaid maid) {
        super(Component.literal("女仆编辑: " + maid.getName().getString()));
        this.parent = parent;
        this.maid = maid;
        this.groupNames = MaidEditorRegistry.getGroups();
    }

    @Override
    protected void init() {
        startX = (width - COLS * (FIELD_W + GAP_X) + GAP_X) / 2;
        startY = 70;
        colW = FIELD_W + GAP_X;

        int navY = 35;
        addRenderableWidget(Button.builder(Component.literal("← 返回"), b -> onClose())
                .pos(5, navY - 10).size(46, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀ 上一组"), b -> prevGroup())
                .pos(width / 2 - 110, navY - 10).size(50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("下一组 ▶"), b -> nextGroup())
                .pos(width / 2 + 60, navY - 10).size(50, 20).build());

        if (!groupNames.isEmpty()) {
            addRenderableWidget(CycleButton.builder((String s) -> Component.literal(s))
                    .withValues(groupNames).withInitialValue(groupNames.get(groupIdx))
                    .create(width / 2 - 58, navY - 10, 116, 20, Component.literal(""),
                            (b, v) -> switchGroup(v)));
        }

        addRenderableWidget(Button.builder(Component.literal("应用"),
                b -> applyCurrentGroup()).pos(width - 130, height - 26).size(50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("保存"),
                b -> saveAndClose()).pos(width - 70, height - 26).size(50, 20).build());

        buildFields();
    }

    private String currentGroupName() {
        return groupIdx < groupNames.size() ? groupNames.get(groupIdx) : "";
    }

    private List<MaidEditorRegistry.EditorField> currentFields() {
        return MaidEditorRegistry.getFields(currentGroupName());
    }

    private void buildFields() {
        for (var w : fieldWidgets) removeWidget(w);
        fieldWidgets.clear();

        var fields = currentFields();
        int totalPages = (fields.size() + PER_PAGE - 1) / PER_PAGE;
        if (pageIdx >= totalPages) pageIdx = 0;
        int start = pageIdx * PER_PAGE;
        int end = Math.min(start + PER_PAGE, fields.size());

        if (totalPages > 1) {
            int px = startX + COLS * colW / 2 - 30;
            addRenderableWidget(Button.builder(Component.literal("◀"),
                    __ -> { if (pageIdx > 0) { pageIdx--; buildFields(); } })
                    .pos(px, 60).size(16, 16).build());
            addRenderableWidget(Button.builder(Component.literal("▶"),
                    __ -> { if (pageIdx < totalPages - 1) { pageIdx++; buildFields(); } })
                    .pos(px + 44, 60).size(16, 16).build());
        }

        for (int i = start; i < end; i++) {
            var f = fields.get(i);
            int idx = i - start;
            int col = idx % COLS, row = idx / COLS;
            int x = startX + col * colW, y = startY + row * GAP_Y;
            String val = readField(f);

            AbstractWidget w = switch (f.type()) {
                case BOOL -> {
                    boolean b = "true".equals(val) || "✓".equals(val);
                    yield CycleButton.booleanBuilder(Component.literal("✓"), Component.literal("✗"))
                            .withInitialValue(b).create(x, y + 12, FIELD_W, FIELD_H,
                                    Component.literal(f.label()),
                                    (cb, v) -> fieldCache.put(f.key(), v ? "true" : "false"));
                }
                case INT -> {
                    var edit = new EditBox(font, x, y + 12, FIELD_W, FIELD_H, Component.literal(f.key()));
                    edit.setFilter(s -> s.matches("-?\\d*"));
                    edit.setResponder(v -> fieldCache.put(f.key(), v));
                    edit.setValue(val);
                    yield edit;
                }
                case FLOAT -> {
                    var edit = new EditBox(font, x, y + 12, FIELD_W, FIELD_H, Component.literal(f.key()));
                    edit.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
                    edit.setResponder(v -> fieldCache.put(f.key(), v));
                    edit.setValue(val);
                    yield edit;
                }
                case STRING -> {
                    var edit = new EditBox(font, x, y + 12, FIELD_W, FIELD_H, Component.literal(f.key()));
                    edit.setResponder(v -> fieldCache.put(f.key(), v));
                    edit.setValue(val);
                    yield edit;
                }
            };
            addRenderableWidget(w);
            fieldWidgets.add(w);
        }
    }

    private String readField(MaidEditorRegistry.EditorField f) {
        return f.reader().apply(maid);
    }

    /** 应用：将当前页字段值写入女仆（不 rebuild，不清编辑状态） */
    private void applyCurrentGroup() {
        if (fieldCache.isEmpty()) return;
        var fields = currentFields();
        for (var f : fields) {
            String newVal = fieldCache.remove(f.key());
            if (newVal != null) {
                try { f.writer().accept(maid, newVal); } catch (Exception ignored) {}
            }
        }
    }

    /** 保存：应用所有缓存 + 关闭 */
    private void saveAndClose() {
        if (!fieldCache.isEmpty()) {
            for (var e : new ArrayList<>(fieldCache.entrySet())) {
                for (var f : currentFields()) {
                    if (f.key().equals(e.getKey())) {
                        try { f.writer().accept(maid, e.getValue()); } catch (Exception ignored) {}
                        break;
                    }
                }
            }
            fieldCache.clear();
        }
        onClose();
    }

    private void rebuildFields() {
        for (var w : new ArrayList<>(children())) {
            if (w instanceof AbstractWidget aw && !(aw instanceof Button))
                removeWidget(aw);
        }
        fieldWidgets.clear();
        init();
    }

    private void prevGroup() {
        if (groupNames.isEmpty()) return;
        groupIdx = (groupIdx - 1 + groupNames.size()) % groupNames.size();
        pageIdx = 0; rebuildFields();
    }
    private void nextGroup() {
        if (groupNames.isEmpty()) return;
        groupIdx = (groupIdx + 1) % groupNames.size();
        pageIdx = 0; rebuildFields();
    }
    private void switchGroup(String name) {
        int i = groupNames.indexOf(name);
        if (i >= 0) { groupIdx = i; pageIdx = 0; rebuildFields(); }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        var fields = currentFields();
        if (fields.isEmpty()) { super.render(g, mx, my, pt); return; }

        int totalPages = (fields.size() + PER_PAGE - 1) / PER_PAGE;
        int start = pageIdx * PER_PAGE, end = Math.min(start + PER_PAGE, fields.size());
        int pageCenterX = startX + COLS * colW / 2 - 8;
        g.drawCenteredString(font, (pageIdx + 1) + "/" + totalPages, pageCenterX, 63, 0xCCCCCC);

        for (int i = start; i < end; i++) {
            var f = fields.get(i);
            int idx = i - start, col = idx % COLS, row = idx / COLS;
            int x = startX + col * colW, y = startY + row * GAP_Y;
            g.drawString(font, f.label(), x, y, 0xCCCCCC);
            if (f.secKey() != null) {
                for (var sf : fields) {
                    if (sf.key().equals(f.secKey())) {
                        String txt = (f.secPrefix() != null ? f.secPrefix() : "") + sf.reader().apply(maid);
                        g.drawString(font, txt, x + FIELD_W + 4, y + 16, 0x999999);
                        break;
                    }
                }
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
