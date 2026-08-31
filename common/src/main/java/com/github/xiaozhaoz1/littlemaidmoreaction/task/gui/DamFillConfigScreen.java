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
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int cx = contentX();
        int y = contentY();

        // 模式切换 (ACTION_TOGGLE boolean 取反) — v79.62.2 默认填坝+排水; 无键读默认 true
        boolean cur = getMenu().getConfig().contains(DamFillPipeline.KEY_DRAIN_ENABLED)
                ? getMenu().getConfig().getBoolean(DamFillPipeline.KEY_DRAIN_ENABLED) : false;   // 默认填坝
        modeBtn = addRenderableWidget(Button.builder(
                Component.literal(modeLabel(cur)),
                btn -> {
                    sendToggle(DamFillPipeline.KEY_DRAIN_ENABLED);
                    boolean now = getMenu().getConfig().contains(DamFillPipeline.KEY_DRAIN_ENABLED)
                            ? getMenu().getConfig().getBoolean(DamFillPipeline.KEY_DRAIN_ENABLED) : false;   // 默认填坝
                    getMenu().getConfig().putBoolean(DamFillPipeline.KEY_DRAIN_ENABLED, !now);
                    btn.setMessage(Component.literal(modeLabel(!now)));
                }).pos(cx, y).size(100, 20).build());
    }

    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
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
