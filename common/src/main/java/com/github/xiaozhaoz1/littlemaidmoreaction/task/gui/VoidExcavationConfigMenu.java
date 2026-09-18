package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;
import net.minecraft.world.entity.player.Inventory;

/**
 * v79.62 挖空置域配置容器 — 单女仆区块数配置 (步进按钮 + 恢复全局).
 *
 * <p>区块数存 pipelineConfig "size", 空则用全局 VOID_DEFAULT_CHUNKS.
 * 屏幕经 {@link VoidExcavationConfigScreen} 读写 maid pipelineConfig.
 */
public class VoidExcavationConfigMenu extends LmaTaskConfigContainer {

    public VoidExcavationConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LmaMenus.VOID_EXCAVATION_CONFIG_MENU.get(), containerId, playerInv, maidId);
    }

}
