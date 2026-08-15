package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.task.MaidTaskConfigGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.TaskConfigActionPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
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

    // ── 样板收敛 (v79.61 架构批 C6) — renderBg/坐标/动作发送 ──

    /** 默认 renderBg — 纯委托基类 (原 5 屏空覆写删) */
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int x, int y) {
        super.renderBg(g, partialTick, x, y);
    }

    /** 内容区起点 X (原各屏魔数 leftPos + 88) */
    protected int contentX() {
        return leftPos + 88;
    }

    /** 内容区起点 Y (原各屏魔数 topPos + 34) */
    protected int contentY() {
        return topPos + 34;
    }

    /** 通用动作发送 — 无女仆静默 (原各屏 if (maid != null) 守卫收敛) */
    protected final void sendAction(byte action, CompoundTag payload) {
        EntityMaid maid = getMaid();
        if (maid == null) return;
        TaskConfigActionPacket.send(maid.getId(), getTaskType(), action, payload);
    }

    /** ACTION_SET_INT — payload {"key": key, "value": value} */
    protected final void sendSetInt(String key, int value) {
        CompoundTag p = new CompoundTag();
        p.putString("key", key);
        p.putInt("value", value);
        sendAction(TaskConfigurable.ACTION_SET_INT, p);
    }

    /** ACTION_REMOVE — payload {"key": key} */
    protected final void sendRemove(String key) {
        CompoundTag p = new CompoundTag();
        p.putString("key", key);
        sendAction(TaskConfigurable.ACTION_REMOVE, p);
    }

    /** ACTION_SET_STRING — payload {"key": key, "value": value} */
    protected final void sendSetString(String key, String value) {
        CompoundTag p = new CompoundTag();
        p.putString("key", key);
        p.putString("value", value);
        sendAction(TaskConfigurable.ACTION_SET_STRING, p);
    }

    /** ACTION_TOGGLE — payload {"key": key} */
    protected final void sendToggle(String key) {
        CompoundTag p = new CompoundTag();
        p.putString("key", key);
        sendAction(TaskConfigurable.ACTION_TOGGLE, p);
    }

    /** ACTION_SET_LIST — payload {"key": key, "value": value} (逗号分隔) */
    protected final void sendSetList(String key, String value) {
        CompoundTag p = new CompoundTag();
        p.putString("key", key);
        p.putString("value", value);
        sendAction(TaskConfigurable.ACTION_SET_LIST, p);
    }
}
