package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.vanilla.MaidAttrRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.model.LmaAnimationDef;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.impl.action.visual.PlayWeaponAnimAction;
import littlemaidmoreaction.littlemaidmoreaction.storage.LmaAnimationStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 动作编辑 — v7.1: SelectParam/BoolParam 控件类型正确渲染, 武器动画布局修复。
 */
public final class ActionEditScreen extends Screen {
    private final Screen parent;
    private final ActionStepBuilder original;
    private ActionStepBuilder row;
    private Button typeBtn;
    private int scroll;

    // ── play_anim 专用 ──
    private CycleButton<String> modeBtn;
    private EditBox animEdit, animStartEdit, animCastingEdit, animEndEdit;
    private EditBox durStartEdit, durCastingEdit, durEndEdit;
    private EditBox generalEdit;

    // ── play_weapon_anim 专用 ──
    private final List<WeaponEntry> weaponEntries = new ArrayList<>();
    private int weaponEntryY;

    // ── modify_maid_attr 专用 ──
    private CycleButton<String> maModeBtn;
    private EditBox maAmountEdit;
    private CycleButton<String> maBoolBtn;
    private EditBox maStrEdit;

    // ── 通用参数控件 ──
    private final List<ParamWidget> paramWidgets = new ArrayList<>();

    /** 单个参数控件记录 */
    private static final class ParamWidget {
        final String key;
        CycleButton<String> cycleBtn;
        EditBox editBox;
        ParamWidget(String key) { this.key = key; }
    }

    private static final class WeaponEntry {
        String weaponType;
        CycleButton<String> weaponBtn;
        EditBox animBox;
        Button removeBtn;
        WeaponEntry(String type) { this.weaponType = type; }
    }

    public ActionEditScreen(Screen parent, ActionStepBuilder original) {
        super(Component.literal("编辑动作"));
        this.parent = parent;
        this.original = original;
        this.row = original.copy();
    }

    @Override
    protected void init() {
        int cx = this.width / 2 - 170, y = 36;

        List<IAction> actions = new ArrayList<>(ActionRegistry.getAll());
        if (actions.isEmpty()) {
            addRenderableWidget(Button.builder(
                    Component.literal("错误：动作注册表为空，请确认模组加载正常"),
                    b -> {}).pos(cx, y).size(260, 20).build());
            addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
                    .pos(cx, this.height - 30).size(80, 20).build());
            return;
        }

        IAction curAction = ActionRegistry.get(row.type);
        String curLabel = curAction != null ? curAction.displayName() : row.type;
        typeBtn = Button.builder(Component.literal(curLabel), b ->
                Minecraft.getInstance().setScreen(new SelectionScreen<>(
                        this, "选择动作类型",
                        actions,
                        IAction::displayName,
                        a -> a.category().name(),
                        a -> a.category().name(),
                        selected -> { row.resetTo(selected); rebuild(); })))
                .pos(cx, y).size(260, 20).build();
        addRenderableWidget(typeBtn);
        y += 28;

        if ("play_anim".equals(row.type)) {
            initPlayAnim(cx, y);
        } else if ("play_weapon_anim".equals(row.type)) {
            initPlayWeaponAnim(cx, y);
        } else if ("modify_maid_attr".equals(row.type)) {
            initModifyMaidAttr(cx, y);
        } else {
            initGenericParams(cx, y);
        }

        y = this.height - 30;
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save()).pos(cx, y).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose()).pos(cx + 90, y).size(80, 20).build());
    }

    // ── 通用参数渲染 (根据 TypedParam 类型选择控件) ──

    private void initGenericParams(int cx, int startY) {
        paramWidgets.clear();
        IAction action = ActionRegistry.get(row.type);
        if (action == null) return;
        int y = startY + scroll;

        for (TypedParam<?> p : action.params()) {
            String curVal = row.params.getOrDefault(p.name(), String.valueOf(p.defaultValue()));
            if (p instanceof TypedParam.SelectParam sel) {
                var btn = CycleButton.<String>builder(s -> Component.literal(s))
                    .withValues(sel.options())
                    .create(cx + 70, y, 190, 20, Component.literal(p.displayName()),
                        (b, v) -> row.params.put(p.name(), v));
                btn.setValue(curVal);
                addRenderableWidget(btn);
                ParamWidget pw = new ParamWidget(p.name()); pw.cycleBtn = btn; paramWidgets.add(pw);
            } else if (p instanceof TypedParam.BoolParam) {
                var btn = CycleButton.<String>builder(s -> Component.literal("true".equals(s) ? "是" : "否"))
                    .withValues(List.of("true", "false"))
                    .create(cx + 70, y, 190, 20, Component.literal(p.displayName()),
                        (b, v) -> row.params.put(p.name(), v));
                btn.setValue(curVal);
                addRenderableWidget(btn);
                ParamWidget pw = new ParamWidget(p.name()); pw.cycleBtn = btn; paramWidgets.add(pw);
            } else if ("attribute".equals(p.name())) {
                MaidAttrRegistry.Entry curDef = MaidAttrRegistry.getDef(curVal);
                String label = curDef != null ? curDef.display() + " (" + curDef.key() + ")" : curVal;
                Button attrBtn = Button.builder(Component.literal(label), b ->
                        Minecraft.getInstance().setScreen(new SelectionScreen<>(
                                this, "选择属性",
                                MaidAttrRegistry.getAll(),
                                e -> e.display() + " (" + e.key() + ")",
                                e -> e.category(),
                                e -> e.category(),
                                selected -> {
                                    row.params.put("attribute", selected.key());
                                    rebuild();
                                })))
                        .pos(cx + 70, y).size(190, 20).build();
                addRenderableWidget(attrBtn);
                ParamWidget pw = new ParamWidget(p.name()); paramWidgets.add(pw);
            } else {
                EditBox eb = new EditBox(font, cx + 70, y, 190, 18, Component.literal(p.displayName()));
                eb.setValue(curVal); addRenderableWidget(eb);
                ParamWidget pw = new ParamWidget(p.name()); pw.editBox = eb; paramWidgets.add(pw);
            }
            y += 22;
        }
    }

    // ── modify_maid_attr ──

    private void initModifyMaidAttr(int cx, int startY) {
        maModeBtn = null; maAmountEdit = null; maBoolBtn = null; maStrEdit = null;
        int y = startY;

        // 属性选择按钮（复用 initGenericParams 的 SelectionScreen 逻辑）
        String attrKey = row.params.getOrDefault("attribute", "attack_damage");
        MaidAttrRegistry.Entry curDef = MaidAttrRegistry.getDef(attrKey);
        String label = curDef != null ? curDef.display() + " (" + curDef.key() + ")" : attrKey;
        Button attrBtn = Button.builder(Component.literal(label), b ->
                Minecraft.getInstance().setScreen(new SelectionScreen<>(
                        this, "选择属性",
                        MaidAttrRegistry.getAll(),
                        e -> e.display() + " (" + e.key() + ")",
                        e -> e.category(),
                        e -> e.category(),
                        selected -> {
                            String oldType = MaidAttrRegistry.getDef(
                                row.params.getOrDefault("attribute", "")) != null
                                ? MaidAttrRegistry.getDef(row.params.getOrDefault("attribute", "")).valueType() : "";
                            row.params.put("attribute", selected.key());
                            // 切换属性类型时清除旧的 mode/amount/value
                            if (!selected.valueType().equals(oldType)) {
                                row.params.remove("mode");
                                row.params.remove("amount");
                                row.params.remove("value");
                            }
                            rebuild();
                        })))
                .pos(cx, y).size(260, 20).build();
        addRenderableWidget(attrBtn);
        y += 26;

        if (curDef == null) return;

        switch (curDef.valueType()) {
            case "num" -> {
                String mode = row.params.getOrDefault("mode", "add");
                maModeBtn = CycleButton.<String>builder(s -> Component.literal(switch (s) {
                            case "set" -> "设置 (=)";
                            case "add" -> "增加 (+)";
                            case "multiply" -> "乘以 (×)";
                            case "divide" -> "除以 (÷)";
                            default -> s;
                        })).withValues(List.of("set", "add", "multiply", "divide"))
                        .create(cx, y, 115, 20, Component.literal("运算"),
                                (b, v) -> row.params.put("mode", v));
                maModeBtn.setValue(mode); addRenderableWidget(maModeBtn);

                maAmountEdit = new EditBox(font, cx + 121, y, 139, 18, Component.literal("数值"));
                maAmountEdit.setValue(row.params.getOrDefault("amount", "1.0"));
                addRenderableWidget(maAmountEdit);
            }
            case "bool" -> {
                String boolVal = row.params.getOrDefault("value", "true");
                maBoolBtn = CycleButton.<String>builder(s -> Component.literal("true".equals(s) ? "是" : "否"))
                        .withValues(List.of("true", "false"))
                        .create(cx, y, 260, 20, Component.literal("值"),
                                (b, v) -> row.params.put("value", v));
                maBoolBtn.setValue(boolVal); addRenderableWidget(maBoolBtn);
            }
            case "str" -> {
                maStrEdit = new EditBox(font, cx, y, 260, 18, Component.literal("值"));
                maStrEdit.setValue(row.params.getOrDefault("value", ""));
                addRenderableWidget(maStrEdit);
            }
        }
    }

    // ── play_anim ──

    private void initPlayAnim(int cx, int startY) {
        animEdit = null; animStartEdit = null; animCastingEdit = null; animEndEdit = null;
        durStartEdit = null; durCastingEdit = null; durEndEdit = null;
        modeBtn = null; generalEdit = null;

        int y = startY;
        String mode = row.params.getOrDefault("mode", "INSTANT");

        modeBtn = CycleButton.<String>builder(s ->
                Component.literal("INSTANT".equals(s) ? "一次性动画 (INSTANT)" : "完整动画 (FULL)"))
                .withValues(List.of("INSTANT", "FULL"))
                .create(cx, y, 260, 20, Component.literal("动画模式"),
                        (b, v) -> { row.params.put("mode", v); rebuild(); });
        modeBtn.setValue(mode); addRenderableWidget(modeBtn);
        y += 28;

        boolean isInstant = "INSTANT".equals(mode);
        int editW = 260;

        if (isInstant) {
            animEdit = new EditBox(font, cx, y, editW, 18, Component.literal("动画名"));
            animEdit.setValue(row.params.getOrDefault("anim", "")); addRenderableWidget(animEdit);
            y += 24;
        } else {
            int animW = 190, durW = 36, gap = 6;
            animStartEdit = new EditBox(font, cx, y, animW, 18, Component.literal("开始动画"));
            animStartEdit.setValue(row.params.getOrDefault("anim_start", "")); addRenderableWidget(animStartEdit);
            durStartEdit = new EditBox(font, cx + animW + gap + 26, y, durW, 18, Component.literal("时长"));
            durStartEdit.setValue(row.params.getOrDefault("dur_start", "20")); addRenderableWidget(durStartEdit);
            y += 24;
            animCastingEdit = new EditBox(font, cx, y, animW, 18, Component.literal("施法动画"));
            animCastingEdit.setValue(row.params.getOrDefault("anim_casting", "")); addRenderableWidget(animCastingEdit);
            durCastingEdit = new EditBox(font, cx + animW + gap + 26, y, durW, 18, Component.literal("时长"));
            durCastingEdit.setValue(row.params.getOrDefault("dur_casting", "20")); addRenderableWidget(durCastingEdit);
            y += 24;
            animEndEdit = new EditBox(font, cx, y, animW, 18, Component.literal("结束动画"));
            animEndEdit.setValue(row.params.getOrDefault("anim_end", "")); addRenderableWidget(animEndEdit);
            durEndEdit = new EditBox(font, cx + animW + gap + 26, y, durW, 18, Component.literal("时长"));
            durEndEdit.setValue(row.params.getOrDefault("dur_end", "20")); addRenderableWidget(durEndEdit);
            y += 24;
        }

        y += 4;
        boolean currentAutoWait = "true".equals(row.params.getOrDefault("auto_wait", "true"));
        addRenderableWidget(Button.builder(
            Component.literal("auto_wait: " + (currentAutoWait ? "ON" : "OFF")), b -> {
                boolean newVal = !"true".equals(row.params.getOrDefault("auto_wait", "true"));
                row.params.put("auto_wait", String.valueOf(newVal));
                saveEditBoxesToRow();
                Minecraft.getInstance().setScreen(new ActionEditScreen(parent, row));
            }).pos(cx, y).size(160, 18).build());
    }

    // ── play_weapon_anim ──

    private void initPlayWeaponAnim(int cx, int startY) {
        weaponEntries.clear();
        int y = startY;
        weaponEntryY = y;

        // 从 row.params 恢复已有条目
        Set<String> usedTypes = new LinkedHashSet<>();
        for (String key : row.params.keySet()) {
            if (key.startsWith("wpn_")) usedTypes.add(key.substring(4));
        }

        if (usedTypes.isEmpty()) {
            addWeaponEntry(cx, "sword", "");
        } else {
            for (String type : usedTypes) {
                addWeaponEntry(cx, type, row.params.getOrDefault("wpn_" + type, ""));
            }
        }

        // 添加按钮放在所有条目之后
        addRenderableWidget(Button.builder(Component.literal("+ 添加武器"), b -> {
            saveWeaponEntriesToRow();
            String next = findUnusedType();
            row.params.put("wpn_" + next, "");
            rebuild();
        }).pos(cx, weaponEntryY + 4).size(120, 18).build());
    }

    private void addWeaponEntry(int cx, String type, String animValue) {
        WeaponEntry entry = new WeaponEntry(type);
        int y = weaponEntryY;

        entry.weaponBtn = CycleButton.<String>builder(s -> Component.literal(s))
            .withValues(PlayWeaponAnimAction.WEAPON_TYPES)
            .create(cx, y, 80, 20, Component.literal("武器"),
                (b, v) -> { entry.weaponType = v; });
        entry.weaponBtn.setValue(type);
        addRenderableWidget(entry.weaponBtn);

        entry.animBox = new EditBox(font, cx + 84, y, 130, 18, Component.literal("动画名"));
        entry.animBox.setValue(animValue);
        addRenderableWidget(entry.animBox);

        entry.removeBtn = Button.builder(Component.literal("✕"), b -> {
            saveWeaponEntriesToRow();
            row.params.remove("wpn_" + entry.weaponType);
            rebuild();
        }).pos(cx + 218, y).size(18, 18).build();
        addRenderableWidget(entry.removeBtn);

        weaponEntries.add(entry);
        weaponEntryY += 22;
    }

    private void saveWeaponEntriesToRow() {
        row.params.keySet().removeIf(k -> k.startsWith("wpn_"));
        for (WeaponEntry e : weaponEntries) {
            String val = e.animBox != null ? e.animBox.getValue().trim() : "";
            if (!val.isEmpty()) row.params.put("wpn_" + e.weaponType, val);
        }
    }

    private String findUnusedType() {
        Set<String> used = new HashSet<>();
        for (String k : row.params.keySet()) if (k.startsWith("wpn_")) used.add(k.substring(4));
        for (WeaponEntry e : weaponEntries) used.add(e.weaponType);
        for (String t : PlayWeaponAnimAction.WEAPON_TYPES) if (!used.contains(t)) return t;
        return "default";
    }

    // ── 保存 ──

    private void saveEditBoxesToRow() {
        if (animEdit != null) row.params.put("anim", animEdit.getValue().trim());
        if (animStartEdit != null) row.params.put("anim_start", animStartEdit.getValue().trim());
        if (animCastingEdit != null) row.params.put("anim_casting", animCastingEdit.getValue().trim());
        if (animEndEdit != null) row.params.put("anim_end", animEndEdit.getValue().trim());
        if (durStartEdit != null) row.params.put("dur_start", durStartEdit.getValue().trim());
        if (durCastingEdit != null) row.params.put("dur_casting", durCastingEdit.getValue().trim());
        if (durEndEdit != null) row.params.put("dur_end", durEndEdit.getValue().trim());
    }

    private void save() {
        if ("play_anim".equals(row.type)) {
            saveEditBoxesToRow();
        } else if ("play_weapon_anim".equals(row.type)) {
            saveWeaponEntriesToRow();
        } else if ("modify_maid_attr".equals(row.type)) {
            if (maModeBtn != null) row.params.put("mode", maModeBtn.getValue());
            if (maAmountEdit != null) row.params.put("amount", maAmountEdit.getValue().trim());
            if (maBoolBtn != null) row.params.put("value", maBoolBtn.getValue());
            if (maStrEdit != null) row.params.put("value", maStrEdit.getValue().trim());
        } else {
            for (ParamWidget pw : paramWidgets) {
                if (pw.cycleBtn != null) row.params.put(pw.key, pw.cycleBtn.getValue());
                else if (pw.editBox != null) row.params.put(pw.key, pw.editBox.getValue());
            }
        }
        original.type = row.type;
        original.params.clear();
        original.params.putAll(row.params);
        onClose();
    }

    private void rebuild() { clearWidgets(); paramWidgets.clear(); weaponEntries.clear(); init(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "编辑动作", this.width / 2, 10, 0xFFD700);
        int cx = this.width / 2 - 170;
        g.drawString(font, "类型:", cx - 35, 40, 0xFFAAAAAA);

        if ("play_anim".equals(row.type)) {
            g.drawString(font, "动画模式:", cx - 50, 70, 0xFFAAAAAA);
            int y = 100;
            String mode = row.params.getOrDefault("mode", "INSTANT");
            if ("INSTANT".equals(mode)) { g.drawString(font, "动画名:", cx - 40, y, 0xFFAAAAAA); y += 24; }
            else {
                g.drawString(font, "开始动画:", cx - 50, y, 0xFFAAAAAA);
                g.drawString(font, "时长:", cx + 198, y, 0xFF888888);
                g.drawString(font, "施法动画:", cx - 50, y + 24, 0xFFAAAAAA);
                g.drawString(font, "时长:", cx + 198, y + 24, 0xFF888888);
                g.drawString(font, "结束动画:", cx - 50, y + 48, 0xFFAAAAAA);
                g.drawString(font, "时长:", cx + 198, y + 48, 0xFF888888);
                y += 72;
            }
        } else if ("play_weapon_anim".equals(row.type)) {
            g.drawString(font, "武器动画匹配 (INSTANT):", cx, 70, 0xFFAAAAAA);
        } else if ("modify_maid_attr".equals(row.type)) {
            g.drawString(font, "属性:", cx + 170, 68, 0xFF888888);
            MaidAttrRegistry.Entry curDef = MaidAttrRegistry.getDef(
                row.params.getOrDefault("attribute", ""));
            if (curDef != null) {
                String typeLabel = switch (curDef.valueType()) {
                    case "bool" -> " [是/否]";
                    case "str" -> " [文本]";
                    default -> ""; // num 不显示额外标签
                };
                g.drawString(font, curDef.display() + typeLabel, cx + 210, 68, 0xFFFFFF);
            }
        } else {
            IAction action = ActionRegistry.get(row.type);
            if (action != null) {
                int y = 66 + scroll;
                for (TypedParam<?> p : action.params()) {
                    g.drawString(font, p.displayName() + ":", cx, y + 4, 0xFF888888);
                    y += 22;
                }
            }
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dy) {
        if ("play_anim".equals(row.type) || "play_weapon_anim".equals(row.type)
            || "modify_maid_attr".equals(row.type))
            return super.mouseScrolled(mx, my, dy);
        IAction action = ActionRegistry.get(row.type);
        int n = action != null ? action.params().size() : 0;
        if (n > 5) { int min = Math.min(0, -(n - 5) * 22); scroll += (int)(dy * 20); scroll = Math.max(min, Math.min(0, scroll)); rebuild(); return true; }
        return super.mouseScrolled(mx, my, dy);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
