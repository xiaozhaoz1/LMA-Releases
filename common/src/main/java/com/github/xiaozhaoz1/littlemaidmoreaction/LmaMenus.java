package com.github.xiaozhaoz1.littlemaidmoreaction;

import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BellRingConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BlockInteractConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.CraftChainConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.ItemListConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.PassiveToggleConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigMenu;
import net.minecraft.world.inventory.MenuType;
import java.util.function.Supplier;

/**
 * 菜单类型**供应商**静态持有 (平台中性) — 平台入口注入 "holder/supplier" 本身,
 * **值只在使用点 {@code .get()} 解析**。
 *
 * ★ v79.63 修 (用户实机事故: 装配屏 "界面消失"): 原设计存的是 {@code MenuType} **值**
 * (commonSetup 里 {@code holder.get()} 缓存), 客户端屏注册早于该注入时读到 null ⇒
 * {@code register(null, …)} 静默落在 null 键 ⇒ 原版只报 "Failed to create screen"。
 * 官方注册规范: **持有 holder, 不要缓存它的值** (docs.neoforged.net/docs/concepts/registries)。
 * ⇒ 使用规则: 只允许 {@code LmaMenus.X.get()}; **禁止**把结果再存进任何静态字段。
 * v75.1: 清除迁移遗留的 79 层冗余 //? if 嵌套 (if/} 数量失衡 → stonecutter Unclosed scope)。
 */
public final class LmaMenus {
    /** v56: 便携装配GUI (v75.1 双平台) */
    public static Supplier<MenuType<MaidAssemblyMenu>> MAID_ASSEMBLY_MENU;
    /** v66: BlockInteract 配置菜单 */
    public static Supplier<MenuType<BlockInteractConfigMenu>> BLOCK_INTERACT_CONFIG_MENU;
    /** v67.3: 通用黑白名单配置菜单 */
    public static Supplier<MenuType<ItemListConfigMenu>> ITEM_LIST_CONFIG_MENU;
    /** v67.3: 配方链合成配置菜单 */
    public static Supplier<MenuType<CraftChainConfigMenu>> CRAFT_CHAIN_CONFIG_MENU;
    /** v67.13: 敲钟单女仆间隔配置菜单 */
    public static Supplier<MenuType<BellRingConfigMenu>> BELL_RING_CONFIG_MENU;
    /** v74: AI 操控配置菜单 (LLM 模型/声线名称) */
    public static Supplier<MenuType<AiControlConfigMenu>> AI_CONTROL_CONFIG_MENU;
    /** v79.62.1: 通用被动管线开关配置菜单 (haqi/jiuhu_milk 共用) */
    public static Supplier<MenuType<PassiveToggleConfigMenu>> PASSIVE_TOGGLE_CONFIG_MENU;
    /** v79.62: 挖空置域单女仆区块数配置菜单 */
    public static Supplier<MenuType<VoidExcavationConfigMenu>> VOID_EXCAVATION_CONFIG_MENU;
    /** v79.62.2: dam_fill config menu (drain toggle) */
    public static Supplier<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigMenu>> DAM_FILL_CONFIG_MENU;
    /** v79.62.3: 防御塔 GUI (弹药槽 + 范围/伤害/模式) */
    public static Supplier<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.defense.DefenseTowerMenu>> DEFENSE_TOWER_MENU;

    private LmaMenus() {
    }
}
