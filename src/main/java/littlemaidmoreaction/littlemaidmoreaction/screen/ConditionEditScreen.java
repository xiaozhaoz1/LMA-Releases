package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.vanilla.MaidAttrRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 条件编辑 — v8: 参数编辑支持(SelectParam/BoolParam/EditBox)。
 */
public final class ConditionEditScreen extends Screen {
    private final Screen parent;
    private final ConditionDefBuilder original;
    private ConditionDefBuilder row;
    private Button keyBtn;
    private CycleButton<String> opBtn, keyMathBtn, valMathBtn;
    private EditBox valInput, keyMathValInput, valMathValInput;

    /** 条件特定参数控件 */
    private static final class ParamWidget {
        final String key;
        CycleButton<String> cycleBtn;
        EditBox editBox;
        ParamWidget(String key) { this.key = key; }
    }
    private final List<ParamWidget> paramWidgets = new ArrayList<>();

    private static final String[] OPS_NUM = {":=:", ":<:", ":>:", ":!=:", ":>=:", ":<=:"};
    private static final String[] OPS_STR = {":=:", ":!=:", ":contains:", ":regex:", ":in:"};
    private static final String[] MATH = {"无", "+", "-", "*", "/"};

    public ConditionEditScreen(Screen parent, ConditionDefBuilder original) {
        super(Component.literal("编辑条件"));
        this.parent = parent;
        this.original = original;
        this.row = original.copy();
    }

    @Override
    protected void init() {
        int cx = this.width / 2 - 170, y = 40;

        List<ICondition> conditions = new ArrayList<>(ConditionRegistry.getAll());
        if (conditions.isEmpty()) {
            addRenderableWidget(Button.builder(
                    Component.literal("错误：条件注册表为空，请确认模组加载正常"),
                    b -> {}).pos(cx, y).size(260, 20).build());
            addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose())
                    .pos(cx, this.height - 30).size(80, 20).build());
            return;
        }

        ICondition curCond = ConditionRegistry.get(row.key);
        String curLabel = curCond != null ? curCond.displayName() + " (" + row.key + ")" : row.key;
        keyBtn = Button.builder(Component.literal(curLabel), b ->
                Minecraft.getInstance().setScreen(new SelectionScreen<>(
                        this, "选择条件",
                        conditions,
                        c -> c.displayName() + " (" + c.key() + ")",
                        c -> c.category().name() + "/" + c.valueType().name().charAt(0),
                        c -> c.category().name(),
                        selected -> { row.key = selected.key(); row.adaptOp(); rebuild(); })))
                .pos(cx, y).size(260, 20).build();
        addRenderableWidget(keyBtn);
        y += 26;

        String effectiveType = getEffectiveType();
        if (!"bool".equals(effectiveType)) {
            String[] ops = "str".equals(effectiveType) ? OPS_STR : OPS_NUM;
            opBtn = CycleButton.<String>builder(s -> Component.literal(s))
                    .withValues(ops).create(cx, y, 100, 20, Component.literal("操作符"), (b, v) -> row.op = v);
            opBtn.setValue(row.op); addRenderableWidget(opBtn);
            valInput = new EditBox(font, cx + 110, y, 150, 18, Component.literal("值"));
            valInput.setValue(row.val != null ? row.val : ""); addRenderableWidget(valInput);
            y += 26;
        }

        if ("num".equals(effectiveType)) {
            // 键运算 — 下拉 + 数值输入
            keyMathBtn = CycleButton.<String>builder(s -> Component.literal(s))
                    .withValues(MATH).create(cx, y, 60, 20, Component.literal("键运算"),
                            (b, v) -> {
                                if ("无".equals(v)) { row.keyMath = null; row.keyMathVal = null; }
                                else { row.keyMath = v; if (row.keyMathVal == null) row.keyMathVal = "1"; }
                            });
            keyMathBtn.setValue(row.keyMath != null ? row.keyMath : "无"); addRenderableWidget(keyMathBtn);
            keyMathValInput = new EditBox(font, cx + 64, y, 40, 18, Component.literal("值"));
            keyMathValInput.setValue(row.keyMathVal != null ? row.keyMathVal : "1"); addRenderableWidget(keyMathValInput);

            // 值运算 — 下拉 + 数值输入
            valMathBtn = CycleButton.<String>builder(s -> Component.literal(s))
                    .withValues(MATH).create(cx + 115, y, 60, 20, Component.literal("值运算"),
                            (b, v) -> {
                                if ("无".equals(v)) { row.valMath = null; row.valMathVal = null; }
                                else { row.valMath = v; if (row.valMathVal == null) row.valMathVal = "1"; }
                            });
            valMathBtn.setValue(row.valMath != null ? row.valMath : "无"); addRenderableWidget(valMathBtn);
            valMathValInput = new EditBox(font, cx + 179, y, 40, 18, Component.literal("值"));
            valMathValInput.setValue(row.valMathVal != null ? row.valMathVal : "1"); addRenderableWidget(valMathValInput);
            y += 26;
        }

        // ── Condition params ──
        paramWidgets.clear();
        ICondition cond = ConditionRegistry.get(row.key);
        if (cond != null) {
            for (TypedParam<?> p : cond.params()) {
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
                    eb.setValue(curVal);
                    addRenderableWidget(eb);
                    ParamWidget pw = new ParamWidget(p.name()); pw.editBox = eb; paramWidgets.add(pw);
                }
                y += 22;
            }
        }

        y = this.height - 30;
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save()).pos(cx, y).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose()).pos(cx + 90, y).size(80, 20).build());
    }

    /** 获取条件的实际值类型。对 maid_attr 从 MaidAttrRegistry 动态获取，其他条件使用注册表静态类型。 */
    private String getEffectiveType() {
        if ("maid_attr".equals(row.key)) {
            MaidAttrRegistry.Entry e = MaidAttrRegistry.getDef(row.params.getOrDefault("attribute", ""));
            return e != null ? e.valueType() : "num";
        }
        if (row.isBool()) return "bool";
        if (row.isNum()) return "num";
        return "str";
    }

    private void save() {
        if (valInput != null) row.val = valInput.getValue();
        if (keyMathValInput != null) row.keyMathVal = keyMathValInput.getValue();
        if (valMathValInput != null) row.valMathVal = valMathValInput.getValue();
        original.key = row.key;
        original.keyMath = row.keyMath;
        original.keyMathVal = row.keyMathVal;
        original.op = row.op;
        original.val = row.val;
        original.valMath = row.valMath;
        original.valMathVal = row.valMathVal;
        // 保存 condition params
        for (ParamWidget pw : paramWidgets) {
            if (pw.cycleBtn != null) row.params.put(pw.key, pw.cycleBtn.getValue());
            else if (pw.editBox != null) row.params.put(pw.key, pw.editBox.getValue());
        }
        original.params.clear();
        original.params.putAll(row.params);
        onClose();
    }

    private void rebuild() { clearWidgets(); paramWidgets.clear(); init(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "编辑条件", this.width / 2, 10, 0xFFD700);
        int cx = this.width / 2 - 170;
        g.drawString(font, "条件键:", cx - 47, 44, 0xFFAAAAAA);
        String et = getEffectiveType();
        if (!"bool".equals(et)) g.drawString(font, "操作符/值:", cx - 55, 70, 0xFFAAAAAA);
        if ("num".equals(et))  g.drawString(font, "键运算/值运算:", cx - 82, 96, 0xFFAAAAAA);
        super.render(g, mx, my, pt);
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
