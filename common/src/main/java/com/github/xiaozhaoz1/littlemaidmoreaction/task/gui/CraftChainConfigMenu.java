package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;

import net.minecraft.world.entity.player.Inventory;

/**
 * v67.3: 配方链合成配置容器 — 当前产物 (TASK_TARGET) + 产物上限 (pipelineConfig max_products)。
 */
public class CraftChainConfigMenu extends LmaTaskConfigContainer {

    public CraftChainConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LmaMenus.CRAFT_CHAIN_CONFIG_MENU, containerId, playerInv, maidId);
    }

}
