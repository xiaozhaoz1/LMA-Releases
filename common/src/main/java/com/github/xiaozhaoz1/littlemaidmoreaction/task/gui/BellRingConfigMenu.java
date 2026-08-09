package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import net.minecraft.world.entity.player.Inventory;

/**
 * v67.13: 敲钟单女仆配置容器 — 对齐 BlockInteractConfigMenu 模式。
 *
 * <p>间隔存 pipelineConfig "ring_interval", 空则用全局 BELL_RING_INTERVAL。
 * 屏幕经 {@link BellRingConfigScreen} 直接读写 maid pipelineConfig。
 */
public class BellRingConfigMenu extends LmaTaskConfigContainer {

    public BellRingConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LmaMenus.BELL_RING_CONFIG_MENU, containerId, playerInv, maidId);
    }

}
