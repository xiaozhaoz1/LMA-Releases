package com.github.xiaozhaoz1.littlemaidmoreaction;

import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BellRingConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BlockInteractConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.CraftChainConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.ItemListConfigMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * 菜单类型静态持有 (平台中性) — loader 入口构造时注入注册值。
 * v75.1: 清除迁移遗留的 79 层冗余 //? if 嵌套 (if/} 数量失衡 → stonecutter Unclosed scope)。
 */
public final class LmaMenus {
    /** v56: 便携装配GUI (v75.1 双平台) */
    public static MenuType<MaidAssemblyMenu> MAID_ASSEMBLY_MENU;
    /** v66: BlockInteract 配置菜单 */
    public static MenuType<BlockInteractConfigMenu> BLOCK_INTERACT_CONFIG_MENU;
    /** v67.3: 通用黑白名单配置菜单 */
    public static MenuType<ItemListConfigMenu> ITEM_LIST_CONFIG_MENU;
    /** v67.3: 配方链合成配置菜单 */
    public static MenuType<CraftChainConfigMenu> CRAFT_CHAIN_CONFIG_MENU;
    /** v67.13: 敲钟单女仆间隔配置菜单 */
    public static MenuType<BellRingConfigMenu> BELL_RING_CONFIG_MENU;
    /** v74: AI 操控配置菜单 (LLM 模型/声线名称) */
    public static MenuType<AiControlConfigMenu> AI_CONTROL_CONFIG_MENU;
    /** v76 Phase 6: FSM 任务自动配置菜单 (参数契约驱动) */

    private LmaMenus() {
    }
}
