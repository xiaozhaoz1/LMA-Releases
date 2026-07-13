package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import java.util.*;

/**
 * 规则列表编辑器 — 显示所有规则，支持新增/编辑/删除。
 *
 * 每条规则行: id | 启用标记 | 名称 | 事件 | 条件数 | 动作数 | [edit] [del]
 * 底部按钮: Done(返回) / On:Y/N(规则引擎总开关,直接写ForgeConfig)
 * 支持鼠标滚轮滚动列表。
 */
public final class MainEditorScreen extends Screen {
    private final Screen parent;
    private int px, py, pw, ph, scroll;
    private static final int IH = 42;

    public MainEditorScreen(Screen parent) {
        super(Component.literal("规则编辑器")); this.parent = parent;
    }

    @Override
    protected void init() {
        pw = (int)(this.width * 0.7);
        px = (this.width - pw) / 2;
        py = 36;
        ph = this.height - 100; // 底部留空间给"完成"和"引擎"按钮

        addRenderableWidget(Button.builder(Component.literal("新增规则"), b -> openRule(-1))
                .pos(px + pw - 80, py - 20).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复预设"), b -> restorePresets())
                .pos(px + pw - 170, py - 20).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .pos(this.width / 2 - 100, this.height - 30).size(100, 20).build());

        boolean on = MoreActionConfig.CUSTOM_RULES_ENABLED.get();
        addRenderableWidget(Button.builder(Component.literal("引擎: " + (on ? "开" : "关")), b -> {
            boolean v = !MoreActionConfig.CUSTOM_RULES_ENABLED.get();
            MoreActionConfig.CUSTOM_RULES_ENABLED.set(v);
            MoreActionConfig.SPEC.save();
            b.setMessage(Component.literal("引擎: " + (v ? "开" : "关")));
        }).pos(this.width / 2 + 10, this.height - 30).size(100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "规则编辑器", this.width / 2, 10, 0xFFD700);
        List<RuleDef> rs = RuleActionStorage.getRules();
        g.fill(px, py, px + pw, py + ph, 0xAA1A1A1A);
        g.renderOutline(px, py, pw, ph, 0xFF666666);
        g.drawString(font, "规则 (" + rs.size() + ")", px + 4, py + 4, 0x55AAFF);
        g.fill(px + 2, py + 18, px + pw - 2, py + 20, 0xFF555555);
        g.enableScissor(px + 2, py + 20, px + pw - 2, py + ph - 2);
        int ry = py + 22 + scroll;
        for (RuleDef r : rs) {
            boolean h = mx >= px + 4 && mx <= px + pw - 4 && my >= ry && my <= ry + IH - 2;
            g.fill(px + 4, ry, px + pw - 4, ry + IH - 2, h ? 0xFF555555 : 0xFF333333);
            int sc = r.enabled() ? 0xFF55FF55 : 0xFFFF5555;
            g.drawString(font, "#" + r.id() + " " + (r.enabled() ? "O" : "X") + " " + r.name(),
                    px + 8, ry + 4, sc);
            String info = r.eventId() + " | c:" + r.conditions().size() + " a:" + r.actions().size();
            g.drawString(font, info, px + 8, ry + 18, 0xFF888888);
            if (h) {
                g.drawString(font, "[编辑]", px + pw - 70, ry + 4, 0xFF55FF55);
                g.drawString(font, "[删除]", px + pw - 70, ry + 20, 0xFFFF5555);
            }
            ry += IH;
        }
        g.disableScissor();
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        List<RuleDef> rs = RuleActionStorage.getRules();
        // 仅处理列表区域内的点击，底部按钮区放行给 super
        if (mx >= px && mx <= px + pw && my >= py + 20 && my <= py + ph) {
            int ry = py + 22 + scroll;
            for (RuleDef r : rs) {
                if (my >= ry && my <= ry + IH - 2) {
                    if (mx >= px + pw - 74) {
                        if (my < ry + 20) openRule(r.id());
                        else deleteRule(r.id());
                    }
                    return true;
                }
                ry += IH;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dy) {
        if (mx >= px && mx <= px + pw) {
            int total = RuleActionStorage.getRules().size() * IH;
            int min = Math.min(0, ph - 20 - total);
            scroll += (int)(dy * 20); scroll = Math.max(min, Math.min(0, scroll));
            return true;
        }
        return super.mouseScrolled(mx, my, dy);
    }

    private void openRule(int id) { Minecraft.getInstance().setScreen(new RuleEditScreen(this, id)); }
    private void deleteRule(int id) {
        RuleDef target = RuleActionStorage.getRules().stream()
                .filter(r -> r.id() == id).findFirst().orElse(null);
        if (target == null) return;
        Minecraft.getInstance().setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        List<RuleDef> l = new ArrayList<>(RuleActionStorage.getRules());
                        l.removeIf(r -> r.id() == id);
                        RuleActionStorage.replaceRules(l);
                        // 删除后钳位 scroll，防止内容变少后显示空白
                        int total = l.size() * IH;
                        int min = Math.min(0, ph - 20 - total);
                        scroll = Math.max(min, Math.min(0, scroll));
                    }
                    Minecraft.getInstance().setScreen(this);
                },
                Component.literal("确认删除"),
                Component.literal("删除规则「" + target.name() + "」(#" + id + ")？此操作不可撤销。")
        ));
    }

    private void restorePresets() {
        Minecraft.getInstance().setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) RuleActionStorage.restorePresets();
                    Minecraft.getInstance().setScreen(this);
                },
                Component.literal("确认恢复"),
                Component.literal("恢复预设规则？\n所有自定义规则将被清除，此操作不可撤销！")
        ));
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
