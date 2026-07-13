package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ActionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 动作列表 — v5: 默认动作类型从 ActionRegistry 获取。
 * v8.5: 支持 ↑↓ 调整动作顺序。
 */
public final class ActionListScreen extends Screen {
    private final Screen parent;
    private final List<ActionStepBuilder> acts;
    private int scroll;
    private static final int LH = 22, MAXW = 360, START_Y = 40;

    public ActionListScreen(Screen parent, List<ActionStepBuilder> acts) {
        super(Component.literal("动作列表"));
        this.parent = parent; this.acts = acts;
    }

    @Override
    protected void init() {
        int cx = cx();
        addRenderableWidget(Button.builder(Component.literal("[+添加]"), b -> {
            IAction def = ActionRegistry.get("play_anim");
            ActionStepBuilder ar;
            if (def != null) {
                ar = new ActionStepBuilder(def.id());
                def.params().forEach(p -> ar.params.put(p.name(), String.valueOf(p.defaultValue())));
            } else {
                ar = new ActionStepBuilder("play_anim");
            }
            acts.add(ar);
        }).pos(cx + MAXW - 60, START_Y - 16).size(60, 16).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .pos(cx, this.height - 30).size(80, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "动作列表 (" + acts.size() + ")", this.width / 2, 10, 0xFFD700);
        int cx = cx(), y = START_Y + scroll;
        g.enableScissor(cx, START_Y, cx + MAXW, this.height - 35);
        for (int i = 0; i < acts.size(); i++) {
            ActionStepBuilder a = acts.get(i);
            boolean hr = mx >= cx && mx <= cx + MAXW && my >= y && my <= y + LH - 2;
            g.fill(cx, y, cx + MAXW, y + LH - 2, hr ? 0xFF555555 : (i % 2 == 0 ? 0xFF2A2A2A : 0xFF333333));
            // label — 给箭头留空间，截断宽度缩小
            String label = a.label();
            if (font.width(label) > MAXW - 125) label = font.plainSubstrByWidth(label, MAXW - 125) + "...";
            g.drawString(font, label, cx + 4, y + 4, 0xFFFFFFFF);
            // ↑ 上移按钮 (首行灰色)
            boolean hUp = i > 0 && mx >= cx + MAXW - 100 && mx <= cx + MAXW - 88 && my >= y && my <= y + LH;
            g.drawString(font, i > 0 ? "↑" : " ", cx + MAXW - 98, y + 4, i > 0 ? (hUp ? 0xFF55FF55 : 0xFF55AA55) : 0xFF555555);
            // ↓ 下移按钮 (末行灰色)
            boolean hDn = i < acts.size() - 1 && mx >= cx + MAXW - 85 && mx <= cx + MAXW - 73 && my >= y && my <= y + LH;
            g.drawString(font, i < acts.size() - 1 ? "↓" : " ", cx + MAXW - 83, y + 4, i < acts.size() - 1 ? (hDn ? 0xFF55FF55 : 0xFF55AA55) : 0xFF555555);
            // [编辑] + X
            boolean hE = mx >= cx + MAXW - 65 && mx <= cx + MAXW - 40 && my >= y && my <= y + LH;
            boolean hX = mx >= cx + MAXW - 30 && mx <= cx + MAXW - 5 && my >= y && my <= y + LH;
            g.drawString(font, "[编辑]", cx + MAXW - 65, y + 4, hE ? 0xFF55FF55 : 0xFF55AA55);
            g.drawString(font, "X", cx + MAXW - 15, y + 4, hX ? 0xFFFF3333 : 0xFFFF5555);
            y += LH;
        }
        g.disableScissor();
        super.render(g, mx, my, pt);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int cx = cx(), y = START_Y + scroll;
        for (int i = 0; i < acts.size(); i++) {
            if (my >= y && my <= y + LH) {
                // ↑ 上移
                if (mx >= cx + MAXW - 100 && mx <= cx + MAXW - 88 && i > 0) {
                    java.util.Collections.swap(acts, i, i - 1);
                    return true;
                }
                // ↓ 下移
                if (mx >= cx + MAXW - 85 && mx <= cx + MAXW - 73 && i < acts.size() - 1) {
                    java.util.Collections.swap(acts, i, i + 1);
                    return true;
                }
                // [编辑]
                if (mx >= cx + MAXW - 65 && mx <= cx + MAXW - 40)
                    Minecraft.getInstance().setScreen(new ActionEditScreen(this, acts.get(i)));
                // X 删除
                else if (mx >= cx + MAXW - 30 && mx <= cx + MAXW - 5)
                    acts.remove(i);
                return true;
            }
            y += LH;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dy) {
        int total = acts.size() * LH;
        int min = Math.min(0, this.height - 60 - total);
        scroll += (int)(dy * 20); scroll = Math.max(min, Math.min(0, scroll));
        return true;
    }

    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
    private int cx() { return this.width / 2 - 170; }
}
