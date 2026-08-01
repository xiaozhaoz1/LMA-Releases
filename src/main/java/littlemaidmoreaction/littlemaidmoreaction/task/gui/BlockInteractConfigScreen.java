package littlemaidmoreaction.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.network.RequestTaskConfigPacket;
import littlemaidmoreaction.littlemaidmoreaction.network.TaskConfigActionPacket;
import littlemaidmoreaction.littlemaidmoreaction.task.api.TaskPipeline;
import littlemaidmoreaction.littlemaidmoreaction.task.pipeline.BlockInteractPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import littlemaidmoreaction.littlemaidmoreaction.config.ActiveTaskConfig;

/**
 * BlockInteract 配置屏幕.
 *
 * <p>Parchment/Mojang 映射冲突导致无法调 {@code super.renderBg()},
 * 改为在 renderBg 中手动绘制 TLM 主背景纹理.
 * 控件通过 {@code initAdditionWidgets} + {@code renderAddition} 添加.
 * 配置修改通过引擎通用动作 (TaskPipeline.ACTION_*) 发送, 服务端由
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

    // ── renderBg: 自绘 TLM 风格框架 (主背景/女仆3D/血量条) + LMA 控件 ──

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int x, int y) {
        // TLM renderBg 反射在本环境 (Embeddium) 静默失败 — 直接 blit TLM 纹理自绘框架
        drawMaidFramework(g, x, y, partialTick);
        // LMA 控件通过 initAdditionWidgets (按钮) + renderAddition (文字) 绘制
    }

    // ── initAdditionWidgets: 请求配置 + 添加按钮 (TLM 标准钩子) ──

    @Override
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), getTaskType());

        int cx = leftPos + 88;
        int y = topPos + 34;

        timerToggleBtn = Button.builder(getTimerLabel(), btn -> {
            CompoundTag cfg = getMenu().getConfig();
            boolean cur = cfg.getBoolean(BlockInteractPipeline.KEY_TIMER_ENABLED);
            cfg.putBoolean(BlockInteractPipeline.KEY_TIMER_ENABLED, !cur);
            if (m != null) {
                CompoundTag payload = new CompoundTag();
                payload.putString("key", BlockInteractPipeline.KEY_TIMER_ENABLED);
                TaskConfigActionPacket.send(m.getId(), getTaskType(),
                        TaskPipeline.ACTION_TOGGLE, payload);
            }
        }).pos(cx, y).size(80, 20).build();
        addRenderableWidget(timerToggleBtn);

        y += 22;
        int bx = cx;
        addRenderableWidget(Button.builder(Component.literal("-10"),
            btn -> changeInterval(m, -10)).pos(bx, y).size(30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-1"),
            btn -> changeInterval(m, -1)).pos(bx + 34, y).size(25, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+1"),
            btn -> changeInterval(m, +1)).pos(bx + 64, y).size(25, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+10"),
            btn -> changeInterval(m, +10)).pos(bx + 94, y).size(30, 20).build());

        y += 24;
        addRenderableWidget(Button.builder(Component.literal("§c清除绑定"), btn -> {
            getMenu().getConfig().remove(BlockInteractPipeline.KEY_POS);
            if (m != null) {
                CompoundTag payload = new CompoundTag();
                payload.putString("key", BlockInteractPipeline.KEY_POS);
                TaskConfigActionPacket.send(m.getId(), getTaskType(),
                        TaskPipeline.ACTION_REMOVE, payload);
            }
        }).pos(cx, y).size(80, 20).build());
    }

    // ── renderAddition: 从 menu.config 渲染文字 (TLM 标准钩子) ──

    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        CompoundTag cfg = getMenu().getConfig();
        int cx = leftPos + 88;
        int y = topPos + 100;
        g.drawString(font, Component.literal("绑定: " + getPosText(cfg)), cx, y, 0xFFFFFF);
        y += 14;
        int interval = cfg.getInt(BlockInteractPipeline.KEY_TIMER_INTERVAL);
        if (interval <= 0) interval = ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.get();
        g.drawString(font, Component.literal("间隔: " + interval + " tick"), cx, y, 0xFFFFFF);
        y += 14;
        g.drawString(font, Component.literal("按键: 默认 0 (选项→控制)"), cx, y, 0xAAAAAA);
        timerToggleBtn.setMessage(getTimerLabel());
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    // ── 业务 ──

    private void changeInterval(EntityMaid maid, int delta) {
        CompoundTag cfg = getMenu().getConfig();
        int cur = cfg.getInt(BlockInteractPipeline.KEY_TIMER_INTERVAL);
        if (cur <= 0) cur = ActiveTaskConfig.BI_TIMER_DEFAULT_INTERVAL.get();
        int next = Math.max(1, cur + delta);
        cfg.putInt(BlockInteractPipeline.KEY_TIMER_INTERVAL, next);
        if (maid != null) {
            CompoundTag payload = new CompoundTag();
            payload.putString("key", BlockInteractPipeline.KEY_TIMER_INTERVAL);
            payload.putInt("value", next);
            TaskConfigActionPacket.send(maid.getId(), getTaskType(), TaskPipeline.ACTION_SET_INT, payload);
        }
    }

    private Component getTimerLabel() {
        return getMenu().getConfig().getBoolean(BlockInteractPipeline.KEY_TIMER_ENABLED)
            ? Component.literal("§a定时器: 开") : Component.literal("§7定时器: 关");
    }

    private String getPosText(CompoundTag cfg) {
        if (!cfg.contains(BlockInteractPipeline.KEY_POS)) return "未绑定";
        BlockPos pos = NbtUtils.readBlockPos(cfg.getCompound(BlockInteractPipeline.KEY_POS));
        return pos.toShortString();
    }
}
