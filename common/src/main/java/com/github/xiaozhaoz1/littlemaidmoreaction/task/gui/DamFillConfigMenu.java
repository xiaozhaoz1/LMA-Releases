package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;

import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;
import net.minecraft.world.entity.player.Inventory;

/**
 * v79.62.2 填坝排水配置容器 — 排水开关 (drain_enabled) 等 per-maid 配置.
 * 屏幕经 {@link DamFillConfigScreen} 读写 maid pipelineConfig (lma_cfg_dam_fill).
 */
public class DamFillConfigMenu extends LmaTaskConfigContainer {

    public DamFillConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LmaMenus.DAM_FILL_CONFIG_MENU.get(), containerId, playerInv, maidId);
    }

}
