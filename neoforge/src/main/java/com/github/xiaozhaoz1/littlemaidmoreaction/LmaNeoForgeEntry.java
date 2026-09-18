package com.github.xiaozhaoz1.littlemaidmoreaction;

import com.github.xiaozhaoz1.littlemaidmoreaction.api.MoreActionAPI;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaRegistrar;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.NeoNetworkHandler;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.NeoNetworkSender;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BellRingConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BlockInteractConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.CraftChainConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.ItemListConfigMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * NeoForge 1.21.1 入口 — 对应 forge 侧 {@code LittleMaidMoreAction} (@Mod 主入口)。
 * <p>payload 网络 + 6 MenuType + 3 config 注册。</p>
 */
@Mod(LittleMaidMoreAction.MOD_ID)
public final class LmaNeoForgeEntry {

    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, LittleMaidMoreAction.MOD_ID);

    public static final Supplier<MenuType<BlockInteractConfigMenu>> BLOCK_INTERACT_CONFIG_MENU =
            MENU_TYPES.register("block_interact_config", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new BlockInteractConfigMenu(id, inv, buf.readInt())));
    public static final Supplier<MenuType<ItemListConfigMenu>> ITEM_LIST_CONFIG_MENU =
            MENU_TYPES.register("item_list_config", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new ItemListConfigMenu(id, inv, buf.readInt())));
    public static final Supplier<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.PassiveToggleConfigMenu>> PASSIVE_TOGGLE_CONFIG_MENU =
            MENU_TYPES.register("passive_toggle_config", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.PassiveToggleConfigMenu(id, inv, buf.readInt())));
    public static final Supplier<MenuType<CraftChainConfigMenu>> CRAFT_CHAIN_CONFIG_MENU =
            MENU_TYPES.register("craft_chain_config", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new CraftChainConfigMenu(id, inv, buf.readInt())));
    public static final Supplier<MenuType<BellRingConfigMenu>> BELL_RING_CONFIG_MENU =
            MENU_TYPES.register("bell_ring_config", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new BellRingConfigMenu(id, inv, buf.readInt())));
    /** v79.62.2: 填坝排水配置菜单 (排水开关) */
    public static final Supplier<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigMenu>> DAM_FILL_CONFIG_MENU =
            MENU_TYPES.register("dam_fill_config", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigMenu(id, inv, buf.readInt())));
    /** v79.62: 挖空置域单女仆区块数配置菜单 */
    public static final Supplier<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigMenu>> VOID_EXCAVATION_CONFIG_MENU =
            MENU_TYPES.register("void_excavation_config", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigMenu(id, inv, buf.readInt())));
    /** v79.62.3: 防御塔 GUI (弹药槽 + 范围/伤害/模式; buf = BlockPos) */
    public static final Supplier<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.defense.DefenseTowerMenu>> DEFENSE_TOWER_MENU =
            MENU_TYPES.register("defense_tower", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new com.github.xiaozhaoz1.littlemaidmoreaction.defense.DefenseTowerMenu(id, inv, buf)));
    public static final Supplier<MenuType<AiControlConfigMenu>> AI_CONTROL_CONFIG_MENU =
            MENU_TYPES.register("ai_control_config", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new AiControlConfigMenu(id, inv, buf.readInt())));
    /** v75.1: 便携装配 GUI (running_belt/assembly 双平台化) */
    public static final Supplier<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu>> MAID_ASSEMBLY_MENU =
            MENU_TYPES.register("maid_assembly", () -> IMenuTypeExtension.create(
                    com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu::new));

    public LmaNeoForgeEntry(IEventBus modBus, ModContainer modContainer) {
        // 3 个 spec 注册 (文件名对齐 forge 侧; CONFIG_DIR 已在 common 定义)
        modContainer.registerConfig(ModConfig.Type.COMMON, MoreActionConfig.SPEC,
                LittleMaidMoreAction.MOD_ID + "-common.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, ActiveTaskConfig.ACTIVE_SPEC,
                LittleMaidMoreAction.MOD_ID + "/active.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, PassiveTaskConfig.PASSIVE_SPEC,
                LittleMaidMoreAction.MOD_ID + "/passive.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON,
                com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.WildKitsuneMilkConfig.KITSUNE_SPEC,
                LittleMaidMoreAction.MOD_ID + "/kitsune_milk.toml");
        // v79.62.3: 防御塔配置段
        modContainer.registerConfig(ModConfig.Type.COMMON,
                com.github.xiaozhaoz1.littlemaidmoreaction.config.DefenseTowerConfig.SPEC,
                LittleMaidMoreAction.MOD_ID + "/defense_tower.toml");

        // 通用注册 (common 双平台代码)
        LmaRegistrar.init();
        LmaRegistrar.initServer();
        LmaRegistrar.registerSounds(modBus);
        LmaRegistrar.registerMemoryModules(modBus);
        LmaRegistrar.registerBlocks(modBus);
        LmaRegistrar.registerBlockEntityTypes(modBus);
        // v79.22: 女仆饰品物品
        LmaRegistrar.registerItems(modBus);
        // v79.62.3: 配方序列化器 (自定义防御塔合成 — 保留手办 NBT)
        LmaRegistrar.registerRecipeSerializers(modBus);
        // v79.62.3: 防御塔容器能力 (漏斗供弹)
        com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaCapabilities.register(modBus);
        // 酒狐奶物品 (v79.6x)
        com.github.xiaozhaoz1.littlemaidmoreaction.bauble.WildKitsuneMilk.KitsuneMilkItems.register(modBus);
        // LMA 创造栏标签页 (2026-08-16 — 用户裁定「创造栏没 LMA 栏」)
        com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaCreativeTab.register(modBus);
        MENU_TYPES.register(modBus);

        // 网络发送注入 (无注册依赖) — M-4: 统一走 setSender 记注入状态日志
        LmaNetwork.setSender(new NeoNetworkSender());

        // 网络 payload 注册 (MOD 总线)
        modBus.addListener(NeoNetworkHandler::registerPacket);
        // 菜单注入 — 需注册完成后 (ctor 里 DeferredHolder.get() 未绑定会 NPE)
        modBus.addListener(this::commonSetup);

        // 游戏总线: 服务器启动加载任务时长
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e) -> {
            // v79.63 (用户裁定): 玩家破坏方块 → 清附近跳过记录 (女仆跟着下矿, 地形变化比 TTL 快)
            if (e.getPlayer() != null && !e.getLevel().isClientSide()) {
                com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.ChainHarvestExecute
                        .invalidateSkipsNear(e.getPos(), 8);
            }
        });

        // v75.1: "变成假人" 动作委托 (common 管线不引平台代码; 双门控 Numen + YSM, 缺一 → null)
        // v77: CompatToggle 一致性门控 (模块关闭时委托不可达, 置 null 语义明确)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.AiControlPipeline.TRANSFORM_ACTIVATOR =
                com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("numen")
                        && com.github.xiaozhaoz1.littlemaidmoreaction.compat.YsmCompat.isPipelineReady()
                        ? (maid) -> {
                            if (maid.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                                com.github.xiaozhaoz1.littlemaidmoreaction.compat.numen.NumenMaidBridge.transform(maid, sl);
                            }
                        }
                        : null;
    }

    private void onServerStarting(ServerStartingEvent event) {
        com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationDurationManager.loadServerDurations();
        // v79.64: 种菜区域改存女仆 NBT (lma_cfg_farm.regions), 启动不再读全局文件 —
        //   老 config/littlemaidmoreaction/farm_regions.json 由 TaskTickHandler 首个 tick 一次性导入 (导入后改名 .imported)
        // v79.62.1 清空挖空认领池 (运行时状态, 重启后空 — 用户裁定, 防旧认领残留)
        com.github.xiaozhaoz1.littlemaidmoreaction.task.service.harvest.VoidExcavationPool.resetPool();
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        // 平台实现注入 — 注册完成后
        LmaMenus.BLOCK_INTERACT_CONFIG_MENU = BLOCK_INTERACT_CONFIG_MENU;   // 存 supplier (值在使用点解析)
        LmaMenus.ITEM_LIST_CONFIG_MENU = ITEM_LIST_CONFIG_MENU;   // 存 supplier (值在使用点解析)
        LmaMenus.CRAFT_CHAIN_CONFIG_MENU = CRAFT_CHAIN_CONFIG_MENU;   // 存 supplier (值在使用点解析)
        LmaMenus.BELL_RING_CONFIG_MENU = BELL_RING_CONFIG_MENU;   // 存 supplier (值在使用点解析)
        LmaMenus.VOID_EXCAVATION_CONFIG_MENU = VOID_EXCAVATION_CONFIG_MENU;   // 存 supplier (值在使用点解析)
        LmaMenus.DAM_FILL_CONFIG_MENU = DAM_FILL_CONFIG_MENU;   // 存 supplier (值在使用点解析)
        LmaMenus.AI_CONTROL_CONFIG_MENU = AI_CONTROL_CONFIG_MENU;   // 存 supplier (值在使用点解析)
        LmaMenus.PASSIVE_TOGGLE_CONFIG_MENU = PASSIVE_TOGGLE_CONFIG_MENU;   // 存 supplier (值在使用点解析)
        LmaMenus.DEFENSE_TOWER_MENU = DEFENSE_TOWER_MENU;   // 存 supplier (值在使用点解析)   // v79.62.3: 防御塔
        LmaMenus.MAID_ASSEMBLY_MENU = MAID_ASSEMBLY_MENU;   // 存 supplier (值在使用点解析)   // v75.1: 便携装配
    }
}
