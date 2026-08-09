package com.github.xiaozhaoz1.littlemaidmoreaction.task.gui;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;

import net.minecraft.world.entity.player.Inventory;

/**
 * AI 操控 (ai_control) 配置容器 (v74) — LLM 模型/声线名称设置。
 *
 * <p>仅提供容器类型注册 (容器契约见基类 LmaTaskConfigContainer)。
 * 屏幕通过 {@link AiControlConfigScreen} 直接读写 maid pipelineConfig (lma_cfg_ai_control)。
 */
public class AiControlConfigMenu extends LmaTaskConfigContainer {

    public AiControlConfigMenu(int containerId, Inventory playerInv, int maidId) {
        super(LmaMenus.AI_CONTROL_CONFIG_MENU, containerId, playerInv, maidId);
    }

}
