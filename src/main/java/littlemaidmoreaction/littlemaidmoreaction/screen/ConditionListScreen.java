package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件列表 — 显示所有条件，支持添加/编辑/删除。
 */
public final class ConditionListScreen extends Screen {
    private final Screen parent;
    private final List<ConditionDefBuilder> conds;
    private int scroll;
    private static final int LH = 22, MAXW = 360, START_Y = 40;

    public ConditionListScreen(Screen parent, List<ConditionDefBuilder> conds) {
        super(Component.literal("条件列表"));
        this.parent = parent; this.conds = conds;
    }

    @Override
    protected void init() {
        int cx = cx();
        // ── 添加条件按钮（分类选择） ──
        List<ICondition> allConds = new ArrayList<>(ConditionRegistry.getAll());
        addRenderableWidget(Button.builder(Component.literal("[+ 添加条件]"), b ->
                Minecraft.getInstance().setScreen(new SelectionScreen<>(
                        this, "添加条件 (" + allConds.size() + ")",
                        allConds,
                        ICondition::displayName,
                        c -> c.category().name() + "/" + c.valueType().name().charAt(0),
                        c -> c.category().name(),
                        selected -> conds.add(ConditionDefBuilder.fromICondition(selected)))))
                .pos(cx + 135, START_Y - 16).size(205, 16).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .pos(cx, this.height - 30).size(80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "条件列表 (" + conds.size() + ")", this.width / 2, 10, 0xFFD700);
        int cx = cx(), y = START_Y + scroll;
        g.enableScissor(cx, START_Y, cx + MAXW, this.height - 35);

        for (int i = 0; i < conds.size(); i++) {
            ConditionDefBuilder c = conds.get(i);
            boolean hr = mx >= cx && mx <= cx + MAXW && my >= y && my <= y + LH - 2;
            g.fill(cx, y, cx + MAXW, y + LH - 2,
                    hr ? 0xFF555555 : (i % 2 == 0 ? 0xFF2A2A2A : 0xFF333333));

            String label = ConditionDefBuilder.keyDisplayName(c.key);
            if (!c.isBool()) label += " " + c.op + " " + c.val;
            if (font.width(label) > MAXW - 100)
                label = font.plainSubstrByWidth(label, MAXW - 100) + "...";
            g.drawString(font, label, cx + 4, y + 4, 0xFFFFFFFF);
            g.drawString(font, c.catTag(), cx + MAXW - 90, y + 4, c.catColor());

            boolean hE = mx >= cx + MAXW - 65 && mx <= cx + MAXW - 40 && my >= y && my <= y + LH;
            boolean hX = mx >= cx + MAXW - 30 && mx <= cx + MAXW - 5 && my >= y && my <= y + LH;
            g.drawString(font, "[编辑]", cx + MAXW - 65, y + 4,
                    hE ? 0xFF55FF55 : 0xFF55AA55);
            g.drawString(font, "X", cx + MAXW - 15, y + 4,
                    hX ? 0xFFFF3333 : 0xFFFF5555);
            y += LH;
        }
        g.disableScissor();
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int cx = cx(), y = START_Y + scroll;
        for (int i = 0; i < conds.size(); i++) {
            if (my >= y && my <= y + LH) {
                if (mx >= cx + MAXW - 65 && mx <= cx + MAXW - 40)
                    Minecraft.getInstance().setScreen(new ConditionEditScreen(this, conds.get(i)));
                else if (mx >= cx + MAXW - 30 && mx <= cx + MAXW - 5)
                    conds.remove(i);
                return true;
            }
            y += LH;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dy) {
        int total = (conds.size() + 1) * LH;
        int min = Math.min(0, this.height - 60 - total);
        scroll += (int)(dy * 20); scroll = Math.max(min, Math.min(0, scroll));
        return true;
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    private int cx() { return this.width / 2 - 170; }
}
