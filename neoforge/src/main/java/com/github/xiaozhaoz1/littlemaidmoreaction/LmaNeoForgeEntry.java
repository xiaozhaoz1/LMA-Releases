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
 * NeoForge 1.21.1 入口 — 对应 forge 侧 {@code LmaForgeEntry}。
 * <p>payload 网络 + 4 MenuType (MaidAssembly 1.20.1 专属) + 3 config 注册。</p>
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
    public static final Supplier<MenuType<CraftChainConfigMenu>> CRAFT_CHAIN_CONFIG_MENU =
            MENU_TYPES.register("craft_chain_config", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new CraftChainConfigMenu(id, inv, buf.readInt())));
    public static final Supplier<MenuType<BellRingConfigMenu>> BELL_RING_CONFIG_MENU =
            MENU_TYPES.register("bell_ring_config", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new BellRingConfigMenu(id, inv, buf.readInt())));
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

        // 通用注册 (common 双平台代码)
        LmaRegistrar.init();
        LmaRegistrar.initServer();
        LmaRegistrar.registerSounds(modBus);
        LmaRegistrar.registerMemoryModules(modBus);
        LmaRegistrar.registerBlocks(modBus);
        LmaRegistrar.registerBlockEntityTypes(modBus);
        // v79.22: 女仆饰品物品
        LmaRegistrar.registerItems(modBus);
        MENU_TYPES.register(modBus);

        // 网络发送注入 (无注册依赖)
        LmaNetwork.sender = new NeoNetworkSender();

        // 网络 payload 注册 (MOD 总线)
        modBus.addListener(NeoNetworkHandler::registerPacket);
        // 菜单注入 — 需注册完成后 (ctor 里 DeferredHolder.get() 未绑定会 NPE)
        modBus.addListener(this::commonSetup);

        // 游戏总线: 服务器启动加载任务时长
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);

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
        MoreActionAPI.loadServerDurations();
    }

    private void commonSetup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        // 平台实现注入 — 注册完成后
        LmaMenus.BLOCK_INTERACT_CONFIG_MENU = BLOCK_INTERACT_CONFIG_MENU.get();
        LmaMenus.ITEM_LIST_CONFIG_MENU = ITEM_LIST_CONFIG_MENU.get();
        LmaMenus.CRAFT_CHAIN_CONFIG_MENU = CRAFT_CHAIN_CONFIG_MENU.get();
        LmaMenus.BELL_RING_CONFIG_MENU = BELL_RING_CONFIG_MENU.get();
        LmaMenus.AI_CONTROL_CONFIG_MENU = AI_CONTROL_CONFIG_MENU.get();
        LmaMenus.MAID_ASSEMBLY_MENU = MAID_ASSEMBLY_MENU.get();   // v75.1: 便携装配
    }
}
