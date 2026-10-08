package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.api.TaskConfigurable;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * v79.62.1 通用被动管线开关配置屏 — haqi/jiuhu_milk 共用.
 * 含「启用/禁用」开关按钮, 存 per-maid pipelineConfig "enabled" 键 (lma_cfg_&lt;taskType&gt;).
 */
public class PassiveToggleConfigScreen extends LmaTaskConfigScreen<PassiveToggleConfigMenu> {

    private final String taskType;
    private Button toggleBtn;

    public PassiveToggleConfigScreen(PassiveToggleConfigMenu menu, Inventory playerInv, Component title, String taskType) {
        super(menu, playerInv, title);
        this.taskType = taskType;
    }

    @Override
    protected String getTaskType() {
        return taskType;
    }

    @Override
    /** 布局 (规范 v79.63.8): 行0 开关 (满宽 rowW) */
    protected void initAdditionWidgets() {
        int rowW = contentRight() - contentX();   // v79.63.22: 控件占满整行 ✓
        int rowX = contentX();
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), taskType);
        toggleBtn = tipBtn(getToggleLabel(),
                rowX, rowY(0) - 2, rowW, () -> {
                    CompoundTag cfg = getMenu().getConfig();
                    boolean cur = cfg.getBoolean(TaskConfigurable.KEY_ENABLED);
                    cfg.putBoolean(TaskConfigurable.KEY_ENABLED, !cur);
                    sendToggle(TaskConfigurable.KEY_ENABLED);
                }, net.minecraft.network.chat.Component.translatable("screen.littlemaidmoreaction.passive.tip"));
        addRenderableWidget(toggleBtn);
    }

    private Component getToggleLabel() {
        boolean on = getMenu().getConfig().getBoolean(TaskConfigurable.KEY_ENABLED);
        return Component.literal(on ? "§a✔ 启用" : "§c✘ 禁用");
    }

    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        if (toggleBtn != null) toggleBtn.setMessage(getToggleLabel());
        super.renderAddition(g, mouseX, mouseY, partialTicks);
    }
}
