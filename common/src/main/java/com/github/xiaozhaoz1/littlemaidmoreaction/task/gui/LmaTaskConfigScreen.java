package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.task.MaidTaskConfigGui;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * LMA 任务配置屏幕基类 — 直接继承 TLM {@link MaidTaskConfigGui}, 基类 renderBg 渲染完整框架。
 *
 * <p>LMA-MAIN 全量采用 TLM 映射 (Parchment), 旧 v67 (Mojang映射) 的映射冲突自绘 hack
 * ({@code TaskConfigGui.drawMaidFramework}) 已删除 — 基类 {@code super.renderBg()} 直接可用。
 *
 * <h3>子类覆写清单</h3>
 * <ol>
 *   <li>{@link #getTaskType} — 返回 task_type 字符串 (配置请求/响应)</li>
 *   <li>{@link #renderBg} — 调 {@code super.renderBg} 后绘制自定义面板</li>
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
}
