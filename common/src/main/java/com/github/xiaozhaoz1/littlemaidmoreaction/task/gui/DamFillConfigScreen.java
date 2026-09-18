package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.DamFillPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * v79.62.2 填坝排水配置屏 — 模式切换: 填坝+排水 (drain_enabled=true, 默认) / 只填坝 (false).
 */

public class DamFillConfigScreen extends LmaTaskConfigScreen<DamFillConfigMenu> {
    /** v79.63.15: 区域区块数输入框 (单女仆覆盖 ✓) */
    private net.minecraft.client.gui.components.EditBox sizeBox;
    private boolean sizeSynced = false;

    /** 模式切换按钮 (renderAddition 按当前配置同步文案) */
    private Button modeBtn;

    public DamFillConfigScreen(DamFillConfigMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected String getTaskType() {
        return "dam_fill";
    }

    /** 模式文案 (v79.62.2 两独立模式): drain_enabled true = 排水模式(只排水); false = 填坝模式(只筑墙) */
    private static String modeLabel(boolean drain) {
        return drain ? "模式: 排水" : "模式: 填坝";
    }

    @Override
    /** 布局 (规范 v79.63.8): 行0 模式切换 (填坝/排水, 满宽 rowW) */
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int rowW = contentRight() - contentX();   // v79.63.22 (用户): 控件占满整行 ✓
        int rowX = contentX();
        boolean cur = getMenu().getConfig().contains(DamFillPipeline.KEY_DRAIN_ENABLED)
                ? getMenu().getConfig().getBoolean(DamFillPipeline.KEY_DRAIN_ENABLED) : false;
        modeBtn = addRenderableWidget(tipBtn(net.minecraft.network.chat.Component.literal(modeLabel(cur)),
                rowX, rowY(0) - 2, rowW, () -> {
                    sendToggle(DamFillPipeline.KEY_DRAIN_ENABLED);
                    boolean now = getMenu().getConfig().contains(DamFillPipeline.KEY_DRAIN_ENABLED)
                            ? getMenu().getConfig().getBoolean(DamFillPipeline.KEY_DRAIN_ENABLED) : false;
                    getMenu().getConfig().putBoolean(DamFillPipeline.KEY_DRAIN_ENABLED, !now);
                    modeBtn.setMessage(net.minecraft.network.chat.Component.literal(modeLabel(!now)));
                }, net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.dam_fill.mode.tip")));

        // ★ v79.63.15 (用户裁定): 填坝排水**自己的区域设置** (不再与挖空置域共用 ✗ / 不再写死 1 ✗)
        //   布局 (规范): 标签 row1 (renderAddition ✓) · 输入框 row2 · 保存按钮 row3 ✓
        sizeBox = tipBox(rowX, rowY(2) - 2, rowW,
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.dam_fill.size"),
                net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.dam_fill.size.tip"));
        sizeBox.setMaxLength(3);
        addRenderableWidget(sizeBox);
        addRenderableWidget(tipBtn(net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.dam_fill.save_size"),
                rowX, rowY(3) - 2, rowW, () -> {
                    String val = sizeBox.getValue() == null ? "" : sizeBox.getValue().trim();
                    if (val.isEmpty()) {
                        sendRemove(DamFillPipeline.KEY_SIZE);
                        getMenu().getConfig().remove(DamFillPipeline.KEY_SIZE);
                    } else {
                        try {
                            int v = Math.max(1, Math.min(256, Integer.parseInt(val)));
                            sendSetInt(DamFillPipeline.KEY_SIZE, v);
                            getMenu().getConfig().putInt(DamFillPipeline.KEY_SIZE, v);
                        } catch (NumberFormatException ex) { /* 非法数字忽略 */ }
                    }
                }, net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.dam_fill.save_size.tip")));
    }
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        // v79.63.15: 区域大小标签 (row1 独占一行 ✓) + 配置到达后预填输入框 ✓
        drawLabel(g, net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.dam_fill.size"), 1);
        if (!sizeSynced && !getMenu().getConfig().isEmpty()) {
            int sz = getMenu().getConfig().contains(DamFillPipeline.KEY_SIZE)
                    ? getMenu().getConfig().getInt(DamFillPipeline.KEY_SIZE)
                    : com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig.DAM_FILL_DEFAULT_CHUNKS.get();
            if (sizeBox != null) sizeBox.setValue(String.valueOf(sz));
            sizeSynced = true;
        }
        var cfg = getMenu().getConfig();
        // 模式文案按当前配置同步 (ReplyTaskConfigPacket 到达前初始默认)
        if (modeBtn != null) {
            boolean n = cfg.contains(DamFillPipeline.KEY_DRAIN_ENABLED)
                    ? cfg.getBoolean(DamFillPipeline.KEY_DRAIN_ENABLED) : false;   // 默认填坝
            String label = modeLabel(n);
            if (!modeBtn.getMessage().getString().equals(label)) {
                modeBtn.setMessage(Component.literal(label));
            }
        }
        // v79.62.2 提示已删 — TLM 任务栏按钮本身是「模式: 填坝/排水」切换, 无需额外 label 解释
    }
}
