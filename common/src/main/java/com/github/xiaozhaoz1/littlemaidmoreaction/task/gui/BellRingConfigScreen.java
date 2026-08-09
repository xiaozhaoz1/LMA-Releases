package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.TaskConfigActionPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskPipeline;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.BellRingPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * v67.13: 敲钟单女仆间隔配置屏幕 — 步进按钮 + 恢复全局。
 *
 * <p>间隔存 pipelineConfig "ring_interval" (-1 不存在 = 用全局 BELL_RING_INTERVAL),
 * 经引擎通用动作 ACTION_SET_INT / ACTION_REMOVE 写入。
 */
public class BellRingConfigScreen extends LmaTaskConfigScreen<BellRingConfigMenu> {

    public BellRingConfigScreen(BellRingConfigMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected String getTaskType() {
        return "bell_ring";
    }

    // ── renderBg: TLM 基类渲染完整框架 ──

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int x, int y) {
        super.renderBg(g, partialTick, x, y);
    }

    // ── initAdditionWidgets: 请求配置 + 步进按钮 ──

    @Override
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int cx = leftPos + 88;
        int y = topPos + 34;

        addRenderableWidget(Button.builder(Component.literal("-100"),
                btn -> changeInterval(m, -100)).pos(cx, y).size(46, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-10"),
                btn -> changeInterval(m, -10)).pos(cx + 50, y).size(30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+10"),
                btn -> changeInterval(m, +10)).pos(cx + 84, y).size(30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+100"),
                btn -> changeInterval(m, +100)).pos(cx + 118, y).size(30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("恢复全局"),
                btn -> resetToGlobal(m)).pos(cx, y + 24).size(70, 20).build());
    }

    // ── renderAddition: 显示当前间隔 (per-maid/全局) ──

    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        CompoundTag cfg = getMenu().getConfig();
        int interval = cfg.contains(BellRingPipeline.KEY_RING_INTERVAL)
                ? cfg.getInt(BellRingPipeline.KEY_RING_INTERVAL)
                : ActiveTaskConfig.BELL_RING_INTERVAL.get();
        String text = "敲钟间隔: " + interval + " tick ("
                + (cfg.contains(BellRingPipeline.KEY_RING_INTERVAL) ? "单女仆" : "全局") + ")";
        // v67.14: y 70→84 — 避开「恢复全局」按钮 (58-78)
        g.drawString(font, Component.literal(text), leftPos + 88, topPos + 84, 0xFFFFFF);
    }

    // ── 业务 ──

    private void changeInterval(EntityMaid maid, int delta) {
        CompoundTag cfg = getMenu().getConfig();
        int cur = cfg.contains(BellRingPipeline.KEY_RING_INTERVAL)
                ? cfg.getInt(BellRingPipeline.KEY_RING_INTERVAL)
                : ActiveTaskConfig.BELL_RING_INTERVAL.get();
        int next = Math.max(30, Math.min(12000, cur + delta));
        cfg.putInt(BellRingPipeline.KEY_RING_INTERVAL, next);
        if (maid != null) {
            CompoundTag payload = new CompoundTag();
            payload.putString("key", BellRingPipeline.KEY_RING_INTERVAL);
            payload.putInt("value", next);
            TaskConfigActionPacket.send(maid.getId(), getTaskType(),
                    TaskPipeline.ACTION_SET_INT, payload);
        }
    }

    /** 清除 per-maid 覆盖 (回全局) */
    private void resetToGlobal(EntityMaid maid) {
        getMenu().getConfig().remove(BellRingPipeline.KEY_RING_INTERVAL);
        if (maid != null) {
            CompoundTag payload = new CompoundTag();
            payload.putString("key", BellRingPipeline.KEY_RING_INTERVAL);
            TaskConfigActionPacket.send(maid.getId(), getTaskType(),
                    TaskPipeline.ACTION_REMOVE, payload);
        }
    }
}
