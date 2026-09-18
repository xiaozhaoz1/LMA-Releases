package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.BlockInteractPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * BlockInteract 配置屏幕.
 *
 * <p>Parchment/Mojang 映射冲突导致无法调 {@code super.renderBg()},
 * 改为在 renderBg 中手动绘制 TLM 主背景纹理.
 * 控件通过 {@code initAdditionWidgets} + {@code renderAddition} 添加.
 * 配置修改通过引擎通用动作 (TaskConfigurable.ACTION_*) 发送, 服务端由
 * {@link TaskPipeline#handleConfigAction} 默认实现处理.
 * 定时器默认间隔 (v67.2): {@link MoreActionConfig#BI_TIMER_DEFAULT_INTERVAL}.
 */
public class BlockInteractConfigScreen extends LmaTaskConfigScreen<BlockInteractConfigMenu> {

    private Button timerToggleBtn;

    public BlockInteractConfigScreen(BlockInteractConfigMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected String getTaskType() {
        return "block_interact";
    }

    // ── initAdditionWidgets: 请求配置 + 添加按钮 (TLM 标准钩子; renderBg 基类默认委托) ──

    /**
     * 布局 (规范 v79.63.8 + **标签/控件分行** — 修用户实测"标签重叠" ✗):
     * 行0 绑定方块标签 (renderAddition 写值) · 行1 定时间隔标签 · 行2 定时开关 (满宽) · 行3 间隔 ± 步进 (四等分) · 行4 清除绑定。
     *
     * <p>⚠ 原实现把标签画在 row0/row1、按钮也放 row0/row1 ⇒ **同排重叠** (错题 #331 第 7 条同款) ✗
     * ⇒ 标签独占行, 控件整体下移 (挂钟屏同规范)。
     */
    @Override
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int rowW = contentRight() - contentX();
        int rowX = contentX();

        timerToggleBtn = tipBtn(getTimerLabel(), rowX, rowY(2) - 2, rowW, () -> {
            CompoundTag cfg = getMenu().getConfig();
            boolean cur = cfg.getBoolean(BlockInteractPipeline.KEY_TIMER_ENABLED);
            cfg.putBoolean(BlockInteractPipeline.KEY_TIMER_ENABLED, !cur);
            sendToggle(BlockInteractPipeline.KEY_TIMER_ENABLED);
        }, Component.translatable("screen.littlemaidmoreaction.bi.timer.tip"));
        addRenderableWidget(timerToggleBtn);

        int w1 = rowW / 4;
        int y1 = rowY(3) - 2;   // v79.64.1: 原 row1 与标签同行 ⇒ 下移到 row3
        addRenderableWidget(tipBtn(Component.literal("-10"), rowX, y1, w1, () -> changeInterval(m, -10),
                Component.translatable("screen.littlemaidmoreaction.bi.interval.tip")));
        addRenderableWidget(tipBtn(Component.literal("-1"), rowX + w1, y1, w1, () -> changeInterval(m, -1),
                Component.translatable("screen.littlemaidmoreaction.bi.interval.tip")));
        addRenderableWidget(tipBtn(Component.literal("+1"), rowX + w1 * 2, y1, w1, () -> changeInterval(m, +1),
                Component.translatable("screen.littlemaidmoreaction.bi.interval.tip")));
        addRenderableWidget(tipBtn(Component.literal("+10"), rowX + w1 * 3, y1, rowW - w1 * 3,
                () -> changeInterval(m, +10),
                Component.translatable("screen.littlemaidmoreaction.bi.interval.tip")));

        addRenderableWidget(tipBtn(Component.translatable("screen.littlemaidmoreaction.bi.clear"),
                rowX, rowY(4) - 2, rowW, () -> {
                    getMenu().getConfig().remove(BlockInteractPipeline.KEY_POS);
                    sendRemove(BlockInteractPipeline.KEY_POS);
                }, Component.translatable("screen.littlemaidmoreaction.bi.clear.tip")));
    }

    /** 标签: 行0 = 当前绑定方块 · 行1 = 当前定时间隔 (控件在 row2/3/4, **不同行 ⇒ 不再重叠** ✓) */
    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        CompoundTag cfg = getMenu().getConfig();
        int interval = cfg.getInt(BlockInteractPipeline.KEY_TIMER_INTERVAL);
        if (interval <= 0) interval = ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.get();
        drawLabel(g, Component.translatable("screen.littlemaidmoreaction.bi.bound", getPosText(cfg)), 0);
        drawLabel(g, Component.translatable("screen.littlemaidmoreaction.bi.interval_label", interval), 1);
        timerToggleBtn.setMessage(getTimerLabel());
    }

    // ── 业务 ──

    private void changeInterval(EntityMaid maid, int delta) {
        CompoundTag cfg = getMenu().getConfig();
        int cur = cfg.getInt(BlockInteractPipeline.KEY_TIMER_INTERVAL);
        if (cur <= 0) cur = ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.get();
        int next = Math.max(1, cur + delta);
        cfg.putInt(BlockInteractPipeline.KEY_TIMER_INTERVAL, next);
        sendSetInt(BlockInteractPipeline.KEY_TIMER_INTERVAL, next);
    }

    private Component getTimerLabel() {
        return getMenu().getConfig().getBoolean(BlockInteractPipeline.KEY_TIMER_ENABLED)
            ? Component.literal("§a定时器: 开") : Component.literal("§7定时器: 关");
    }

    private String getPosText(CompoundTag cfg) {
        // ★ v79.64.3: 统一 NbtCodecs (单一真相源) — 原手工解析对坏数据会显示 "0, 0, 0" 而非"未绑定" ✗
        BlockPos pos = com.github.xiaozhaoz1.littlemaidmoreaction.api.nbt.NbtCodecs
                .readBlockPos(cfg, BlockInteractPipeline.KEY_POS);
        return pos == null ? Component.translatable("screen.littlemaidmoreaction.bi.unbound").getString()
                : pos.toShortString();
    }
}
