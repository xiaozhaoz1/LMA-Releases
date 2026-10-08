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
 * 产物上限: per-maid pipelineConfig com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline.KEY_MAX_PRODUCTS (-1=无限), 空则用全局
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
    /** 布局 (规范 v79.63.8): 行0 当前产物 · 行1 应用 · 行2 上限± (四等分) · 行3 无限/恢复全局 (各半) */
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int rowW = contentRight() - contentX();
        int rowX = contentX();
        targetBox = tipBox(rowX, rowY(0) - 2, rowW,
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.craft.target"),
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.craft.target.tip"));
        addRenderableWidget(targetBox);

        addRenderableWidget(tipBtn(net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.craft.apply"),
                rowX, rowY(1) - 2, rowW, () -> applyTarget(m),
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.craft.apply.tip")));

        int w1 = rowW / 4;
        int y2 = rowY(3) - 2;   // ± 按钮 row3 (标签 row2 之下 ✓)   // v79.63.14: 上限标签 row3, 四按钮 row4 ✓
        var maxTip = net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.craft.max.tip");
        addRenderableWidget(tipBtn(net.minecraft.network.chat.Component.literal("-100"), rowX, y2, w1, () -> changeMax(m, -100), maxTip));
        addRenderableWidget(tipBtn(net.minecraft.network.chat.Component.literal("-10"), rowX + w1, y2, w1, () -> changeMax(m, -10), maxTip));
        addRenderableWidget(tipBtn(net.minecraft.network.chat.Component.literal("+10"), rowX + w1 * 2, y2, w1, () -> changeMax(m, +10), maxTip));
        addRenderableWidget(tipBtn(net.minecraft.network.chat.Component.literal("+100"), rowX + w1 * 3, y2, rowW - w1 * 3, () -> changeMax(m, +100), maxTip));

        int half = rowW / 2;
        addRenderableWidget(tipBtn(net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.craft.infinite"),
                rowX, rowY(4) - 2, half - 4, () -> setMax(m, -1),
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.craft.infinite.tip")));
        addRenderableWidget(tipBtn(net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.craft.reset"),
                rowX + half, rowY(4) - 2, rowW - half, () -> setMax(m, Integer.MIN_VALUE),
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.craft.reset.tip")));
    }

    /** 当前产物/上限以标签位显示 (左列) */
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        CompoundTag cfg = getMenu().getConfig();
        if (!synced && !cfg.isEmpty()) { targetBox.setValue(cfg.getString("target")); synced = true; }
        boolean perMaid = cfg.contains(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline.KEY_MAX_PRODUCTS);
        int max = perMaid ? cfg.getInt(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline.KEY_MAX_PRODUCTS) : -1;
        String maxText = max < 0 ? "∞" : String.valueOf(max);
        // v79.63.20 (用户): "当前产物"不再占标签行 — 输入框 hint 已经写着它 ✓
        drawLabel(g, net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.craft.max_label", maxText,
                net.minecraft.network.chat.Component.translatable(perMaid
                        ? "screen.littlemaidmoreaction.scope.per_maid" : "screen.littlemaidmoreaction.scope.global")), 2);   // v79.63.21 (用户): 标签在上 ⇒ row2 (原来 row3 与按钮重叠 ✗) (四按钮移到 row4 ✓)
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
        int cur = cfg.contains(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline.KEY_MAX_PRODUCTS) ? cfg.getInt(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline.KEY_MAX_PRODUCTS) : ActiveTaskConfig.CRAFT_MAX_PRODUCTS.get();
        int next = Math.max(-1, cur + delta);
        setMax(maid, next);
    }

    /** value = -1 无限; Integer.MIN_VALUE = 清除 per-maid (回全局) */
    private void setMax(EntityMaid maid, int value) {
        if (value == Integer.MIN_VALUE) {
            getMenu().getConfig().remove(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline.KEY_MAX_PRODUCTS);
            sendRemove(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline.KEY_MAX_PRODUCTS);
        } else {
            getMenu().getConfig().putInt(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline.KEY_MAX_PRODUCTS, value);
            sendSetInt(com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.CraftChainPipeline.KEY_MAX_PRODUCTS, value);
        }
    }
}
