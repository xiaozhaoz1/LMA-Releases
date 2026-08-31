package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

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
    protected void initAdditionWidgets() {
        final EntityMaid m = getMaid();
        if (m != null) RequestTaskConfigPacket.send(m.getId(), taskType);
        int cx = contentX();
        int y = contentY();
        toggleBtn = Button.builder(getToggleLabel(), btn -> {
            CompoundTag cfg = getMenu().getConfig();
            boolean cur = cfg.getBoolean("enabled");
            cfg.putBoolean("enabled", !cur);
            sendToggle("enabled");
        }).pos(cx, y).size(100, 20).build();
        addRenderableWidget(toggleBtn);
    }

    private Component getToggleLabel() {
        boolean on = getMenu().getConfig().getBoolean("enabled");
        return Component.literal(on ? "§a✔ 启用" : "§c✘ 禁用");
    }

    @Override
    protected void renderAddition(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        if (toggleBtn != null) toggleBtn.setMessage(getToggleLabel());
        super.renderAddition(g, mouseX, mouseY, partialTicks);
    }
}
