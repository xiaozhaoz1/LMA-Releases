package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;

import net.minecraft.world.entity.player.Inventory;

/**
 * BlockInteract 配置容器 — 对齐 TLM AttackTaskConfigContainer 模式。
 *
 * <p>仅提供容器类型注册 (容器契约见基类 LmaTaskConfigContainer)。
 * 屏幕通过 {@link BlockInteractConfigScreen} 直接读写 maid pipelineConfig。
 */
public class BlockInteractConfigMenu extends LmaTaskConfigContainer {

    public BlockInteractConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LmaMenus.BLOCK_INTERACT_CONFIG_MENU, containerId, playerInv, maidId);
    }

}
