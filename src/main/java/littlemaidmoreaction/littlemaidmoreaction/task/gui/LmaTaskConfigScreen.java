package littlemaidmoreaction.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.task.MaidTaskConfigGui;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * LMA 任务配置屏幕基类 — 委托 {@link TaskConfigGui#drawMaidFramework} 自绘 TLM 风格框架。
 *
 * <p>TLM (Parchment映射) 与 LMA (Mojang映射) 映射冲突导致 {@code super.renderBg()} 编译失败；
 * 且反射调用 TLM {@code AbstractMaidContainerGui.renderBg} 在 Embeddium 0.3.31 + Oculus
 * 环境下失败 (v67 实测: 任务设置界面整屏泥土, 主界面却正常 — 反射 invoke 不可靠)。
 * TLM 界面自绘逻辑统一在 {@link TaskConfigGui} (GUI 门面类), 本类只负责调用。
 *
 * <h3>子类覆写清单</h3>
 * <ol>
 *   <li>{@link #getTaskType} — 返回 task_type 字符串 (配置请求/响应)</li>
 *   <li>{@link #renderBg} — 调 {@link #drawMaidFramework} 后绘制自定义面板</li>
 *   <li>{@link #initAdditionWidgets} — 添加按钮 (TLM 标准钩子)</li>
 *   <li>{@link #renderAddition} — 渲染覆盖文字 (TLM 标准钩子)</li>
 * </ol>
 */
public abstract class LmaTaskConfigScreen<T extends TaskConfigContainer> extends MaidTaskConfigGui<T> {

    /** 返回 task_type (如 "block_interact") — 用于 RequestTaskConfigPacket 配置请求 */
    protected abstract String getTaskType();

    public LmaTaskConfigScreen(T screenContainer, Inventory inv, Component titleIn) {
        super(screenContainer, inv, titleIn);
    }

    /**
     * 自绘 TLM 风格 GUI 框架 — 实现见 {@link TaskConfigGui#drawMaidFramework}。
     * 只用 blit + drawString — 禁止裸 fill/fillGradient (Embeddium 顶点缓冲崩溃源)。
     */
    protected final void drawMaidFramework(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // protected 字段 (leftPos/topPos/imageWidth/imageHeight/font) 由子类读取后传入
        TaskConfigGui.drawMaidFramework(this, g, leftPos, topPos, imageWidth, imageHeight,
                font, mouseX, mouseY, partialTick);
    }
}
