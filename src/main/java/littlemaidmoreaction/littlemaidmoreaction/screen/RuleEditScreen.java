package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.core.model.ActionStep;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import littlemaidmoreaction.littlemaidmoreaction.core.model.MatchMode;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.event.RuleEvent;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 规则编辑 — 元数据 + 导航到条件/动作子界面。
 *
 * <p>条件 → {@link ConditionListScreen} → {@link ConditionEditScreen}
 * <br>动作 → {@link ActionListScreen} → {@link ActionEditScreen}</p>
 */
public final class RuleEditScreen extends Screen {
    private final Screen parent;
    private final int editingId;
    private int ruleId;
    private EditBox nameInput, chanceInput, cooldownInput, priorityInput;
    private Button eventButton;
    private boolean enabled = true;
    private MatchMode matchMode = MatchMode.ALL;
    private final List<ConditionDefBuilder> conds = new ArrayList<>();
    private final List<ActionStepBuilder> acts = new ArrayList<>();
    private boolean loaded; // 仅首次 init 从存储加载 conds/acts
    // 缓存首次加载的 metadata，子界面返回后重建 EditBox 时恢复
    private String initName = "", initEvent = "", initChance = "1.0", initCd = "100", initPri = "0";

    // ── 布局 ──
    private static final int LABEL_GAP = 4;
    private static final int FULL_W = 260, HALF_W = 120, SMALL_W = 70;

    public RuleEditScreen(Screen parent, int editingId) {
        super(Component.literal(editingId < 0 ? "新建规则" : "编辑规则"));
        this.parent = parent; this.editingId = editingId;
    }

    @Override
    protected void init() {
        int cx = cx();

        if (!loaded) {
            loaded = true;
            ruleId = editingId >= 0 ? editingId : nextId();
            RuleDef pre = editingId >= 0 ? findRule() : null;
            if (pre != null) {
                enabled = pre.enabled();
                matchMode = pre.matchMode();
                initName = pre.name();
                initEvent = pre.eventId();
                initChance = String.valueOf(pre.chance());
                initCd = String.valueOf(pre.cooldown());
                initPri = String.valueOf(pre.priority());
                pre.conditions().forEach(c -> conds.add(ConditionDefBuilder.fromParts(
                        c.key(), c.keyMath(), c.keyMathVal(),
                        c.op(), c.val(), c.valMath(), c.valMathVal(),
                        c.params())));
                pre.actions().forEach(a -> acts.add(ActionStepBuilder.fromParts(
                        a.typeId(), a.params())));
            }
        }

        // 启用开关
        addRenderableWidget(Button.builder(Component.literal(enabled ? "开" : "关"), b -> {
            enabled = !enabled; b.setMessage(Component.literal(enabled ? "开" : "关"));
        }).pos(cx + FULL_W + 4, 38).size(56, 16).build());

        // 名称
        nameInput = new EditBox(font, cx, 68, FULL_W, 20, Component.literal("名称"));
        nameInput.setValue(initName); addRenderableWidget(nameInput);

        // 事件（按钮→全屏列表选择，29个事件一目了然）
        {
            RuleEvent curEvt = RuleEvent.fromEventId(initEvent);
            String curLabel = curEvt != null ? curEvt.getDisplayName() : initEvent;
            eventButton = Button.builder(Component.literal(curLabel), b ->
                    Minecraft.getInstance().setScreen(new SelectionScreen<>(
                            this, "选择事件",
                            Arrays.asList(RuleEvent.values()),
                            RuleEvent::getDisplayName,
                            selected -> {
                                initEvent = selected.getEventId();
                                b.setMessage(Component.literal(selected.getDisplayName()));
                            })))
                    .pos(cx, 96).size(FULL_W, 20).build();
            addRenderableWidget(eventButton);
        }

        // 概率 + 冷却（同行：标签在左、控件紧跟）
        chanceInput = new EditBox(font, cx, 124, HALF_W, 20, Component.literal("概率"));
        chanceInput.setValue(initChance); addRenderableWidget(chanceInput);
        int cdLabelW = font.width("冷却(tick)");
        int cdX = cx + HALF_W + LABEL_GAP + cdLabelW;
        cooldownInput = new EditBox(font, cdX, 124, HALF_W, 20, Component.literal("冷却"));
        cooldownInput.setValue(initCd); addRenderableWidget(cooldownInput);

        // 匹配模式 + 优先级（同行）
        addRenderableWidget(Button.builder(
                Component.literal(matchMode == MatchMode.ALL ? "全部" : "任一"), b -> {
                    matchMode = matchMode == MatchMode.ALL
                            ? MatchMode.ANY : MatchMode.ALL;
                    b.setMessage(Component.literal(matchMode == MatchMode.ALL ? "全部" : "任一"));
                }).pos(cx, 152).size(HALF_W, 20).build());
        int priLabelW = font.width("优先级");
        priorityInput = new EditBox(font, cx + HALF_W + LABEL_GAP + priLabelW,
                152, SMALL_W, 20, Component.literal("优先级"));
        priorityInput.setValue(initPri); addRenderableWidget(priorityInput);

        // 导航按钮
        int btnW = 145;
        addRenderableWidget(Button.builder(
                Component.literal("条件 (" + conds.size() + ")"), b ->
                        Minecraft.getInstance().setScreen(new ConditionListScreen(this, conds)))
                .pos(cx, 184).size(btnW, 20).build());
        addRenderableWidget(Button.builder(
                Component.literal("动作 (" + acts.size() + ")"), b ->
                        Minecraft.getInstance().setScreen(new ActionListScreen(this, acts)))
                .pos(cx + btnW + 5, 184).size(btnW, 20).build());

        // 底部
        int by = this.height - 28;
        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .pos(cx, by).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .pos(cx + 90, by).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("重置"), b -> resetToSaved())
                .pos(cx + 190, by).size(80, 20).build());
    }

    private void resetToSaved() {
        conds.clear(); acts.clear();
        if (editingId >= 0) {
            RuleDef pre = findRule();
            if (pre != null) {
                enabled = pre.enabled(); matchMode = pre.matchMode();
                initName = pre.name(); initEvent = pre.eventId();
                initChance = String.valueOf(pre.chance());
                initCd = String.valueOf(pre.cooldown());
                initPri = String.valueOf(pre.priority());
                pre.conditions().forEach(c -> conds.add(ConditionDefBuilder.fromParts(
                        c.key(), c.keyMath(), c.keyMathVal(),
                        c.op(), c.val(), c.valMath(), c.valMathVal(),
                        c.params())));
                pre.actions().forEach(a -> acts.add(ActionStepBuilder.fromParts(
                        a.typeId(), a.params())));
            }
        } else {
            initName = ""; initEvent = RuleEvent.values()[0].getEventId();
            initChance = "1.0"; initCd = "100"; initPri = "0";
            enabled = true; matchMode = MatchMode.ALL;
        }
        clearWidgets(); init();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, getTitle(), this.width / 2, 10, 0xFFD700);

        int cx = cx();
        // 标签列右对齐线 = 控件左边界 - 4px
        int r = cx - LABEL_GAP;
        g.drawString(font, "ID: " + ruleId + " (自动)", cx, 24, 0xFF888888);
        g.drawString(font, "名称:",      r - font.width("名称:"),      72, 0xFFAAAAAA);
        g.drawString(font, "事件:",      r - font.width("事件:"),     100, 0xFFAAAAAA);
        g.drawString(font, "概率(0~1):", r - font.width("概率(0~1):"), 128, 0xFFAAAAAA);
        // 同行右侧标签贴在各自控件左边
        int cdLabX = cx + HALF_W + LABEL_GAP;
        int priLabX = cx + HALF_W + LABEL_GAP;
        g.drawString(font, "冷却(tick):", cdLabX,  128, 0xFFAAAAAA);
        g.drawString(font, "优先级:",      priLabX, 156, 0xFFAAAAAA);

        super.render(g, mx, my, pt);
    }

    private void save() {
        String name = nameInput.getValue().trim(); if (name.isEmpty()) name = "未命名";

        List<ConditionDef> cd = new ArrayList<>();
        for (ConditionDefBuilder cr : conds)
            cd.add(new ConditionDef(cr.key, cr.op, cr.val,
                    cr.keyMath, cr.keyMathVal, cr.valMath, cr.valMathVal,
                    new LinkedHashMap<>(cr.params)));

        List<ActionStep> ac = new ArrayList<>();
        for (ActionStepBuilder ar : acts) {
            // ★ v9.0: 保存全部动作（含未加载 compat），不丢数据
            ac.add(new ActionStep(ar.type, new LinkedHashMap<>(ar.params)));
        }

        double ch; try { ch = Double.parseDouble(chanceInput.getValue()); }
        catch (NumberFormatException e) { ch = 1.0; }
        int cdv; try { cdv = Integer.parseInt(cooldownInput.getValue()); }
        catch (NumberFormatException e) { cdv = 100; }
        int pri; try { pri = Integer.parseInt(priorityInput.getValue()); }
        catch (NumberFormatException e) { pri = 0; }

        RuleDef rule = new RuleDef(ruleId, name, enabled, initEvent,
                Math.min(1, Math.max(0, ch)), cdv, pri, matchMode, cd, ac,
                littlemaidmoreaction.littlemaidmoreaction.compat.CompatRegistry.detectCompat(cd, ac));

        List<RuleDef> l = new ArrayList<>(RuleActionStorage.getRules());
        if (editingId >= 0) {
            boolean found = false;
            for (int i = 0; i < l.size(); i++)
                if (l.get(i).id() == editingId) { l.set(i, rule); found = true; break; }
            if (!found) l.add(rule);
        } else l.add(rule);
        RuleActionStorage.replaceRules(l); onClose();
    }

    private int nextId() {
        int[] ids = RuleActionStorage.getRules().stream().mapToInt(RuleDef::id)
                .sorted().toArray();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] != i) return i; // 填第一个缺号
        }
        return ids.length;
    }

    private RuleDef findRule() {
        return RuleActionStorage.getRules().stream()
                .filter(x -> x.id() == editingId).findFirst().orElse(null);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    private int cx() { return this.width / 2 - 180; }
}
