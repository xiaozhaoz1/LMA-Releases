package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.entity.player.Inventory;

/**
 * v67.3: 通用黑白名单配置容器 — 一个 MenuType 服务 furnace/jukebox/arm_transfer。
 *
 * <p>屏幕 ({@link ItemListConfigScreen}) 通过 getTaskType() 区分任务,
 * 配置存各任务 pipelineConfig (lma_cfg_&lt;taskType&gt;) 的 blacklist/whitelist 键。
 */
public class ItemListConfigMenu extends LmaTaskConfigContainer {

    public ItemListConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LmaMenus.ITEM_LIST_CONFIG_MENU, containerId, playerInv, maidId);
    }

}
