package littlemaidmoreaction.littlemaidmoreaction.screen;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.core.cache.MaidRuleIndex;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;
import littlemaidmoreaction.littlemaidmoreaction.storage.MaidRuleStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * v8.7 单女仆独立规则列表编辑器 — 类似 {@code MainEditorScreen}，
 * 但数据源为 {@link MaidRuleStorage}，仅影响该女仆。
 *
 * <p>标题显示女仆名称，便于区分不同女仆的独立规则。</p>
 */
public final class MaidRuleListScreen extends Screen {
    private final EntityMaid maid;
    private final UUID maidUuid;
    private int px, py, pw, ph, scroll;
    private static final int IH = 42;

    public MaidRuleListScreen(EntityMaid maid) {
        super(Component.literal("女仆规则 - " + maid.getName().getString()));
        this.maid = maid;
        this.maidUuid = maid.getUUID();
    }

    @Override
    protected void init() {
        pw = (int)(this.width * 0.7);
        px = (this.width - pw) / 2;
        py = 36;
        ph = this.height - 100;

        addRenderableWidget(Button.builder(Component.literal("新增规则"), b -> openRule(-1))
                .pos(px + pw - 80, py - 20).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .pos(this.width / 2 - 100, this.height - 30).size(100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "女仆规则 - " + maid.getName().getString(),
                this.width / 2, 10, 0xFFD700);
        List<RuleDef> rs = MaidRuleStorage.getRules(maidUuid);
        g.fill(px, py, px + pw, py + ph, 0xAA1A1A1A);
        g.renderOutline(px, py, pw, ph, 0xFF666666);
        g.drawString(font, "独立规则 (" + rs.size() + ")", px + 4, py + 4, 0x55AAFF);
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
        List<RuleDef> rs = MaidRuleStorage.getRules(maidUuid);
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
            int total = MaidRuleStorage.getRules(maidUuid).size() * IH;
            int min = Math.min(0, ph - 20 - total);
            scroll += (int)(dy * 20); scroll = Math.max(min, Math.min(0, scroll));
            return true;
        }
        return super.mouseScrolled(mx, my, dy);
    }

    private void openRule(int id) {
        Minecraft.getInstance().setScreen(new MaidRuleEditScreen(this, id, maidUuid));
    }

    private void deleteRule(int id) {
        RuleDef target = MaidRuleStorage.getRules(maidUuid).stream()
                .filter(r -> r.id() == id).findFirst().orElse(null);
        if (target == null) return;
        Minecraft.getInstance().setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        List<RuleDef> l = new ArrayList<>(MaidRuleStorage.getRules(maidUuid));
                        l.removeIf(r -> r.id() == id);
                        MaidRuleStorage.saveRules(maidUuid, l);
                        MaidRuleIndex.rebuild(maidUuid);
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

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(null); }
}
