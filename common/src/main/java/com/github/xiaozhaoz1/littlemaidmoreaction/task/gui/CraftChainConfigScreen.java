package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * v67.3: 配方链合成配置屏幕 — 显示/编辑当前合成物品 id + 产物数量上限。
 *
 * <p>当前产物: 服务端 {@link CraftChainPipeline#getConfigNbt} 返回 target (TASK_TARGET),
 * 应用通过自定义动作 {@link CraftChainPipeline#ACTION_SET_TARGET} 写入。
 * 产物上限: per-maid pipelineConfig "max_products" (-1=无限), 空则用全局
 * {@link MoreActionConfig#CRAFT_MAX_PRODUCTS}, 经引擎 ACTION_SET_INT 写入。
 */
public class CraftChainConfigScreen extends LmaTaskConfigScreen<CraftChainConfigMenu> {

    private EditBox targetBox;
    /** 配置同步标志 — 首次收到服务端配置后不再覆盖 EditBox (防打字被重写) */
    private boolean synced;

    public CraftChainConfigScreen(CraftChainConfigMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected String getTaskType() {
        return "craft_chain";
    }

    // ── initAdditionWidgets: 请求配置 + 产物编辑 + 上限按钮 (renderBg 基类默认委托) ──

    @Override
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int cx = contentX();
        int y = contentY();

        targetBox = new EditBox(font, cx, y, 140, 20, Component.literal("当前产物"));
        addRenderableWidget(targetBox);
        addRenderableWidget(Button.builder(Component.literal("应用产物"),
                btn -> applyTarget(m)).pos(cx, y + 24).size(80, 20).build());

        y += 56;
        addRenderableWidget(Button.builder(Component.literal("上限-100"),
                btn -> changeMax(m, -100)).pos(cx, y).size(46, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-10"),
                btn -> changeMax(m, -10)).pos(cx + 50, y).size(30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+10"),
                btn -> changeMax(m, +10)).pos(cx + 84, y).size(30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+100"),
                btn -> changeMax(m, +100)).pos(cx + 118, y).size(30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("无限(-1)"),
                btn -> setMax(m, -1)).pos(cx, y + 24).size(70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复全局"),
                btn -> setMax(m, Integer.MIN_VALUE)).pos(cx + 76, y + 24).size(70, 20).build());
    }

    // ── renderAddition: 从 menu.config 同步 ──

    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        CompoundTag cfg = getMenu().getConfig();
        if (!synced && !cfg.isEmpty()) {
            targetBox.setValue(cfg.getString("target"));
            synced = true;
        }
        int max = cfg.contains("max_products") ? cfg.getInt("max_products") : ActiveTaskConfig.CRAFT_MAX_PRODUCTS.get();
        String maxText = max < 0 ? "无限 (-1)" : String.valueOf(max);
        // y 118→140 — 避开第二行按钮 (114-134)
        g.drawString(font, Component.literal("产物上限: " + maxText
                + (cfg.contains("max_products") ? " (单女仆)" : " (全局)")), contentX(), topPos + 140, 0xFFFFFF);
    }

    // ── 业务 ──

    private void applyTarget(EntityMaid maid) {
        String value = targetBox.getValue().trim();
        CompoundTag payload = new CompoundTag();
        payload.putString("value", value);
        sendAction(CraftChainPipeline.ACTION_SET_TARGET, payload);
    }

    private void changeMax(EntityMaid maid, int delta) {
        CompoundTag cfg = getMenu().getConfig();
        int cur = cfg.contains("max_products") ? cfg.getInt("max_products") : ActiveTaskConfig.CRAFT_MAX_PRODUCTS.get();
        int next = Math.max(-1, cur + delta);
        setMax(maid, next);
    }

    /** value = -1 无限; Integer.MIN_VALUE = 清除 per-maid (回全局) */
    private void setMax(EntityMaid maid, int value) {
        if (value == Integer.MIN_VALUE) {
            getMenu().getConfig().remove("max_products");
            sendRemove("max_products");
        } else {
            getMenu().getConfig().putInt("max_products", value);
            sendSetInt("max_products", value);
        }
    }
}
