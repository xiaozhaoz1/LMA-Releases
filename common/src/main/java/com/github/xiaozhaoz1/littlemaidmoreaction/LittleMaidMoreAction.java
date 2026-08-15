package com.github.xiaozhaoz1.littlemaidmoreaction;

import com.github.xiaozhaoz1.littlemaidmoreaction.api.MoreActionAPI;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaRegistrar;
//? if 1.20.1 {
import com.github.xiaozhaoz1.littlemaidmoreaction.network.ForgePacketRegistrar;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.SimpleChannelSender;
//?}
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BellRingConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BlockInteractConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.CraftChainConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.ItemListConfigMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
//? if 1.20.1 {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
//?} else {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
//?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 车万女仆「更多动作」附属模组 — 主入口。
 * <p>forge 1.20.1: @Mod 完整入口 (SimpleChannel 网络 + MenuType + config + LmaRegistrar)。
 * neoforge 1.21.1: 入口为 {@code LmaNeoForgeEntry}, 本类仅提供常量与静态菜单字段。</p>
 */
//? if 1.20.1 {
@Mod(LittleMaidMoreAction.MOD_ID)
//?}
public final class LittleMaidMoreAction {
    public static final String MOD_ID = "littlemaidmoreaction";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 配置文件根目录: config/littlemaidmoreaction/ */
    public static final java.nio.file.Path CONFIG_DIR =
//? if 1.20.1 {
            net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
//?} else {
            net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
//?}

    /**
     * 模组网络通道 (forge only — neoforge 用 payload, 见 LmaNeoForgeEntry)。
     */
//? if 1.20.1 {
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "network"),
            () -> "1.0",
            s -> s.equals("1.0"),
            s -> s.equals("1.0")
    );

    /** MenuType 注册 (便携装配GUI) */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    public static final RegistryObject<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu>> MAID_ASSEMBLY_MENU =
        MENU_TYPES.register("maid_assembly", () -> IForgeMenuType.create(com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu::new));
    /** BlockInteract 配置菜单 */
    public static final RegistryObject<MenuType<BlockInteractConfigMenu>> BLOCK_INTERACT_CONFIG_MENU =
        MENU_TYPES.register("block_interact_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new BlockInteractConfigMenu(id, inv, buf.readInt())));
    /** 通用黑白名单配置菜单 (furnace/jukebox/arm_transfer 共用) */
    public static final RegistryObject<MenuType<ItemListConfigMenu>> ITEM_LIST_CONFIG_MENU =
        MENU_TYPES.register("item_list_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new ItemListConfigMenu(id, inv, buf.readInt())));
    /** 配方链合成配置菜单 */
    public static final RegistryObject<MenuType<CraftChainConfigMenu>> CRAFT_CHAIN_CONFIG_MENU =
        MENU_TYPES.register("craft_chain_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new CraftChainConfigMenu(id, inv, buf.readInt())));
    /** 敲钟单女仆间隔配置菜单 */
    public static final RegistryObject<MenuType<BellRingConfigMenu>> BELL_RING_CONFIG_MENU =
        MENU_TYPES.register("bell_ring_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new BellRingConfigMenu(id, inv, buf.readInt())));
    /** AI 操控配置菜单 (LLM 模型/声线名称) */
    public static final RegistryObject<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu>> AI_CONTROL_CONFIG_MENU =
        MENU_TYPES.register("ai_control_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu(id, inv, buf.readInt())));
//?}

    /** BlockInteract 配置菜单 — 双平台通用引用 (forge 用 RegistryObject, neoforge 用 Supplier) */
//? if 1.20.1 {
    public LittleMaidMoreAction() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MoreActionConfig.SPEC, MOD_ID + "-common.toml");
        // 主动/被动任务配置拆分 — 子文件夹 (Forge 自动建目录)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ActiveTaskConfig.ACTIVE_SPEC, MOD_ID + "/active.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PassiveTaskConfig.PASSIVE_SPEC, MOD_ID + "/passive.toml");

        LmaRegistrar.init();
        LmaRegistrar.initServer();
        LmaRegistrar.registerSounds(modBus);
        LmaRegistrar.registerMemoryModules(modBus);
        LmaRegistrar.registerBlocks(modBus);
        LmaRegistrar.registerBlockEntityTypes(modBus);
        // 女仆饰品物品
        LmaRegistrar.registerItems(modBus);
        // 便携装配 MenuType
        MENU_TYPES.register(modBus);
        // 网络发送注入 (SimpleChannel) — M-4: 统一走 setSender 记注入状态日志
        LmaNetwork.setSender(new SimpleChannelSender(NETWORK));

        // DefaultGeckoAnimationEvent 唯一注册点 (2026-08-11c 裁定: 删 LittleMaidMoreActionExtension
        // AnimationEvents 注解路径 — TLM javadoc 要求构造器手动注册 "事件早于扩展注解识别";
        // v79.18 实证注解 auto-scan 对该事件失效 (neoforge GAME bus 收不到), 双注册 = 每事件双执行)
        // ⚠ 禁 modBus (MOD 总线) 注册 — 仅收 IModBusEvent, DefaultGeckoAnimationEvent 非 → 崩溃 (neoforge 实测 14:42; forge 同限制)
        MinecraftForge.EVENT_BUS.addListener((com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent event)
                -> com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationResourceRegistrar.registerCustomAnimations(event));

        MinecraftForge.EVENT_BUS.register(this);
    }

    /** 注册网络包序列化器 */
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // LmaMenus 注入 (修 v75 迁移遗留 — forge 侧此前从未赋值, 配置屏 MenuType null)
            LmaMenus.BLOCK_INTERACT_CONFIG_MENU = BLOCK_INTERACT_CONFIG_MENU.get();
            LmaMenus.ITEM_LIST_CONFIG_MENU = ITEM_LIST_CONFIG_MENU.get();
            LmaMenus.CRAFT_CHAIN_CONFIG_MENU = CRAFT_CHAIN_CONFIG_MENU.get();
            LmaMenus.BELL_RING_CONFIG_MENU = BELL_RING_CONFIG_MENU.get();
            LmaMenus.AI_CONTROL_CONFIG_MENU = AI_CONTROL_CONFIG_MENU.get();
            LmaMenus.MAID_ASSEMBLY_MENU = MAID_ASSEMBLY_MENU.get();   // v75.1: 便携装配
            // 网络包注册 — 清单驱动 (批次 A: PacketRegistry.DEFS 单一事实源 + ForgePacketRegistrar 循环消费)
            ForgePacketRegistrar.registerAll(NETWORK);

            // 配置屏幕注册已移至 LmaForgeClientEntry (客户端入口 — 专用服务器无 Screen 类)
        });
    }
//?}

//? if 1.20.1 {
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationDurationManager.loadServerDurations();
        LOGGER.info("[LMA] 服务端动画数据加载完成");
    }
//?}
}
