package littlemaidmoreaction.littlemaidmoreaction.screen;

import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.storage.RuleActionStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 独立配置屏幕 — Forge 模组列表点本模组即可打开，不依赖 TLM。
 */
public final class LMAConfigScreen extends Screen {
    private final Screen parent;

    public LMAConfigScreen(Screen parent) {
        super(Component.literal("LittleMaidMoreAction"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 40;

        boolean on = MoreActionConfig.CUSTOM_RULES_ENABLED.get();
        this.addRenderableWidget(Button.builder(
                Component.literal("规则引擎: " + (on ? "ON" : "OFF")),
                btn -> {
                    boolean v = !MoreActionConfig.CUSTOM_RULES_ENABLED.get();
                    MoreActionConfig.CUSTOM_RULES_ENABLED.set(v);
                    MoreActionConfig.SPEC.save();
                    btn.setMessage(Component.literal("规则引擎: " + (v ? "ON" : "OFF")));
                }).pos(cx - 80, y).size(160, 20).build());
        y += 28;

        y += 32; // 占位 — 规则数量在 render() 中用灰色文字绘制

        this.addRenderableWidget(Button.builder(
                Component.literal("打开规则编辑器"),
                btn -> Minecraft.getInstance().setScreen(new MainEditorScreen(this)))
                .pos(cx - 80, y).size(160, 20).build());
        y += 28;

        this.addRenderableWidget(Button.builder(
                Component.literal("女仆编辑器"),
                btn -> Minecraft.getInstance().setScreen(new MaidListScreen(this)))
                .pos(cx - 80, y).size(160, 20).build());
        y += 28;

        this.addRenderableWidget(Button.builder(
                Component.literal("调试: " + (MoreActionConfig.DEBUG_MODE.get() ? "ON" : "OFF")),
                btn -> {
                    boolean v = !MoreActionConfig.DEBUG_MODE.get();
                    MoreActionConfig.DEBUG_MODE.set(v);
                    MoreActionConfig.SPEC.save();
                    RuleActionStorage.syncDebugPresets();
                    btn.setMessage(Component.literal("调试: " + (v ? "ON" : "OFF")));
                }).pos(cx - 80, y).size(160, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("完成"),
                btn -> Minecraft.getInstance().setScreen(parent))
                .pos(cx - 40, this.height - 30).size(80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "LittleMaidMoreAction 配置", width / 2, 15, 0xFFD700);
        int rc = RuleActionStorage.getRules().size();
        g.drawCenteredString(font, "已加载 " + rc + " 条规则", width / 2, 68, 0xFF888888);
        super.render(g, mx, my, pt);
    }
}
