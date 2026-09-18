package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
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

    // ── initAdditionWidgets: 请求配置 + 步进按钮 (renderBg 基类默认委托) ──

    /**
     * 布局 (v79.63.5 统一规范): 起点 y = topPos + 40 · 行距 26 · 控件高 20 · 标签左对齐 contentX()
     * 控件宽 150 右对齐到 contentRight(); 面板(宿主 TLM)不够宽时自动缩到可用宽度 ✓
     */
    @Override
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int labelX = contentX();
        // v79.63.20 (用户): 按钮行**占满整行** (原来只占右侧 88 宽 ⇒ 看着还挤 ✗)
        int rowW = contentRight() - contentX();
        int rowX = contentX();
        int y = rowY(1);   // 旧变量仅在未使用的 y 上 (按钮已改用 rowY(1)) ✗ 留作兼容

        // 行 1: 敲钟间隔 — 4 个步进按钮铺满 rowW (150 时 ≈ 34/30/30/34)
        int w1 = rowW / 4;   // v79.63.14: 标签独占 row0, 四按钮排 row1 (用户: 挤 ✗)
        addRenderableWidget(btn("-100", rowX, rowY(1) - 2, w1, () -> changeInterval(m, -100),
                "减少 100 tick (下限 30)"));
        addRenderableWidget(btn("-10", rowX + w1, rowY(1) - 2, w1, () -> changeInterval(m, -10),
                "减少 10 tick (下限 30)"));
        addRenderableWidget(btn("+10", rowX + w1 * 2, rowY(1) - 2, w1, () -> changeInterval(m, +10),
                "增加 10 tick (上限 12000)"));
        addRenderableWidget(btn("+100", rowX + w1 * 3, rowY(1) - 2, rowW - w1 * 3, () -> changeInterval(m, +100),
                "增加 100 tick (上限 12000)"));

        // 行 2: 恢复全局
        int y2 = rowY(2);   // v79.63.14: 按钮排下移一行 ✓
        addRenderableWidget(btn("恢复全局默认", rowX, y2, rowW, () -> resetToGlobal(m),
                "移除单女仆覆盖, 回到全局设置 (active.ring_interval_ticks)"));
    }

    /** 统一按钮构造 + tooltip (v79.63.5 规范) */
    private static net.minecraft.client.gui.components.Button btn(String text, int x, int y, int w,
                                                                   Runnable onClick, String tooltip) {
        return net.minecraft.client.gui.components.Button.builder(Component.literal(text), b -> onClick.run())
                .pos(x, y).size(Math.max(20, w), 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tooltip)))
                .build();
    }
    // ── renderAddition: 显示当前间隔 (per-maid/全局) ──

    /** 数值显示 — 标签位左对齐 (contentX, 行1 y+4): 当前间隔 + 覆盖状态 ✓ */
    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        CompoundTag cfg = getMenu().getConfig();
        boolean perMaid = cfg.contains(BellRingPipeline.KEY_RING_INTERVAL);
        int interval = perMaid ? cfg.getInt(BellRingPipeline.KEY_RING_INTERVAL)
                : ActiveTaskConfig.BELL_RING_INTERVAL.get();
        String text = "敲钟间隔: " + interval + "t (" + (perMaid ? "单女仆" : "全局") + ")";
        // v79.63.14: 标签独占 row0 ✓ (按钮在 row1 — 不与标签同排 ✗)
        g.drawString(font, Component.literal(text), contentX(), rowY(0) + 6, 0xFFFFFF);
    }
    // ── 业务 ──

    private void changeInterval(EntityMaid maid, int delta) {
        CompoundTag cfg = getMenu().getConfig();
        int cur = cfg.contains(BellRingPipeline.KEY_RING_INTERVAL)
                ? cfg.getInt(BellRingPipeline.KEY_RING_INTERVAL)
                : ActiveTaskConfig.BELL_RING_INTERVAL.get();
        int next = Math.max(30, Math.min(12000, cur + delta));
        cfg.putInt(BellRingPipeline.KEY_RING_INTERVAL, next);
        sendSetInt(BellRingPipeline.KEY_RING_INTERVAL, next);
    }

    /** 清除 per-maid 覆盖 (回全局) */
    private void resetToGlobal(EntityMaid maid) {
        getMenu().getConfig().remove(BellRingPipeline.KEY_RING_INTERVAL);
        sendRemove(BellRingPipeline.KEY_RING_INTERVAL);
    }
}
