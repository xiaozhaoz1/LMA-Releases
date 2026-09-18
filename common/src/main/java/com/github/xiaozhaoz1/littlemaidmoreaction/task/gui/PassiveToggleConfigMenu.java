package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;
import net.minecraft.world.entity.player.Inventory;

/**
 * v79.62.1 通用被动管线开关配置容器 — haqi/jiuhu_milk 共用.
 * 屏幕 ({@link PassiveToggleConfigScreen}) 经 getTaskType() 区分管线,
 * 开关存 per-maid pipelineConfig (lma_cfg_&lt;taskType&gt;) 的 "enabled" 键.
 */
public class PassiveToggleConfigMenu extends LmaTaskConfigContainer {

    public PassiveToggleConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LmaMenus.PASSIVE_TOGGLE_CONFIG_MENU.get(), containerId, playerInv, maidId);
    }

}
