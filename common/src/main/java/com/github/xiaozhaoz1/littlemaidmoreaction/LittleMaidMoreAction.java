package com.github.xiaozhaoz1.littlemaidmoreaction;

import com.github.xiaozhaoz1.littlemaidmoreaction.api.MoreActionAPI;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.ActiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.MoreActionConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.config.PassiveTaskConfig;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaRegistrar;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.AnimFileSyncPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.HaqiOwnerVoicePacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidListQueryPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidListResponsePacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidChatBubblePacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.ConfigSyncPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.InteractTriggerPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.LmaAnimSyncMessage;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.ReplyTaskConfigPacket;
import com.github.xiaozhaoz1.littlemaidmoreaction.network.RequestTaskConfigPacket;
//? if 1.20.1 {
import com.github.xiaozhaoz1.littlemaidmoreaction.network.SimpleChannelSender;
//?}
import com.github.xiaozhaoz1.littlemaidmoreaction.network.TaskConfigActionPacket;
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
import net.minecraftforge.network.NetworkDirection;
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

import java.util.Optional;
import java.util.function.Supplier;

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

    /** v56: MenuType 注册 (便携装配GUI) */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    public static final RegistryObject<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu>> MAID_ASSEMBLY_MENU =
        MENU_TYPES.register("maid_assembly", () -> IForgeMenuType.create(com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu::new));
    /** v66: BlockInteract 配置菜单 */
    public static final RegistryObject<MenuType<BlockInteractConfigMenu>> BLOCK_INTERACT_CONFIG_MENU =
        MENU_TYPES.register("block_interact_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new BlockInteractConfigMenu(id, inv, buf.readInt())));
    /** v67.3: 通用黑白名单配置菜单 (furnace/jukebox/arm_transfer 共用) */
    public static final RegistryObject<MenuType<ItemListConfigMenu>> ITEM_LIST_CONFIG_MENU =
        MENU_TYPES.register("item_list_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new ItemListConfigMenu(id, inv, buf.readInt())));
    /** v67.3: 配方链合成配置菜单 */
    public static final RegistryObject<MenuType<CraftChainConfigMenu>> CRAFT_CHAIN_CONFIG_MENU =
        MENU_TYPES.register("craft_chain_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new CraftChainConfigMenu(id, inv, buf.readInt())));
    /** v67.13: 敲钟单女仆间隔配置菜单 */
    public static final RegistryObject<MenuType<BellRingConfigMenu>> BELL_RING_CONFIG_MENU =
        MENU_TYPES.register("bell_ring_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new BellRingConfigMenu(id, inv, buf.readInt())));
    /** v74: AI 操控配置菜单 (LLM 模型/声线名称) */
    public static final RegistryObject<MenuType<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu>> AI_CONTROL_CONFIG_MENU =
        MENU_TYPES.register("ai_control_config",
            () -> IForgeMenuType.create((id, inv, buf) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu(id, inv, buf.readInt())));
//?} else {
//?}

    /** v66: BlockInteract 配置菜单 — 双平台通用引用 (forge 用 RegistryObject, neoforge 用 Supplier) */
//? if 1.20.1 {
    public LittleMaidMoreAction() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MoreActionConfig.SPEC, MOD_ID + "-common.toml");
        // v67.6: 主动/被动任务配置拆分 — 子文件夹 (Forge 自动建目录)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ActiveTaskConfig.ACTIVE_SPEC, MOD_ID + "/active.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PassiveTaskConfig.PASSIVE_SPEC, MOD_ID + "/passive.toml");

        LmaRegistrar.init();
        LmaRegistrar.initServer();
        LmaRegistrar.registerSounds(modBus);
        LmaRegistrar.registerMemoryModules(modBus);
        LmaRegistrar.registerBlocks(modBus);
        LmaRegistrar.registerBlockEntityTypes(modBus);
        // v79.22: 女仆饰品物品
        LmaRegistrar.registerItems(modBus);
        // v56: 便携装配 MenuType
        MENU_TYPES.register(modBus);
        // 网络发送注入 (SimpleChannel)
        LmaNetwork.sender = new SimpleChannelSender(NETWORK);

        // v79.18: DefaultGeckoAnimationEvent 构造器手动注册到 FORGE 总线 (TLM 注释: 事件早于扩展注解识别 — 防 auto-scan 时序丢事件)
        // ⚠ 禁 modBus (MOD 总线) 注册 — 仅收 IModBusEvent, DefaultGeckoAnimationEvent 非 → 崩溃 (neoforge 实测 14:42; forge 同限制)
        MinecraftForge.EVENT_BUS.addListener((com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent event)
                -> com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationResourceRegistrar.registerCustomAnimations(event));

        MinecraftForge.EVENT_BUS.register(this);
    }

    /** 注册网络包序列化器 */
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // v76 Phase 6: LmaMenus 注入 (修 v75 迁移遗留 — forge 侧此前从未赋值, 配置屏 MenuType null)
            LmaMenus.BLOCK_INTERACT_CONFIG_MENU = BLOCK_INTERACT_CONFIG_MENU.get();
            LmaMenus.ITEM_LIST_CONFIG_MENU = ITEM_LIST_CONFIG_MENU.get();
            LmaMenus.CRAFT_CHAIN_CONFIG_MENU = CRAFT_CHAIN_CONFIG_MENU.get();
            LmaMenus.BELL_RING_CONFIG_MENU = BELL_RING_CONFIG_MENU.get();
            LmaMenus.AI_CONTROL_CONFIG_MENU = AI_CONTROL_CONFIG_MENU.get();
            NETWORK.registerMessage(0, LmaAnimSyncMessage.class,
                    LmaAnimSyncMessage::encode,
                    LmaAnimSyncMessage::decode,
                    LmaAnimSyncMessage::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            NETWORK.registerMessage(2, InteractTriggerPacket.class,
                    InteractTriggerPacket::encode,
                    InteractTriggerPacket::decode,
                    InteractTriggerPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
            NETWORK.registerMessage(3, TaskConfigActionPacket.class,
                    TaskConfigActionPacket::encode,
                    TaskConfigActionPacket::decode,
                    TaskConfigActionPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
            NETWORK.registerMessage(5, RequestTaskConfigPacket.class,
                    RequestTaskConfigPacket::encode,
                    RequestTaskConfigPacket::decode,
                    RequestTaskConfigPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
            NETWORK.registerMessage(6, ReplyTaskConfigPacket.class,
                    ReplyTaskConfigPacket::encode,
                    ReplyTaskConfigPacket::decode,
                    ReplyTaskConfigPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            // v67.11: 配置同步 (C→S 保存推送 / S→C 广播), 双向注册
            NETWORK.registerMessage(7, ConfigSyncPacket.class,
                    ConfigSyncPacket::encode,
                    ConfigSyncPacket::decode,
                    ConfigSyncPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
            NETWORK.registerMessage(8, ConfigSyncPacket.class,
                    ConfigSyncPacket::encode,
                    ConfigSyncPacket::decode,
                    ConfigSyncPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            // v79.18: 动画文件同步 (S→C, 专用服务器自定义动画文件推送)
            NETWORK.registerMessage(9, AnimFileSyncPacket.class,
                    AnimFileSyncPacket::encode,
                    AnimFileSyncPacket::decode,
                    AnimFileSyncPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            // v79.20: 对主人哈气语音 (S→C, maidId+volume → 客户端 peco 包 idle 子集随机播放)
            NETWORK.registerMessage(10, HaqiOwnerVoicePacket.class,
                    HaqiOwnerVoicePacket::encode,
                    HaqiOwnerVoicePacket::decode,
                    HaqiOwnerVoicePacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            // v79.20: 女仆表情气泡通用包 (S->C, maidId+表情类型 -> 客户端 maid 实体上加气泡)
            NETWORK.registerMessage(11, MaidChatBubblePacket.class,
                    MaidChatBubblePacket::encode,
                    MaidChatBubblePacket::decode,
                    MaidChatBubblePacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            // v79.25.2: 女仆列表查询 (C→S, 服务端全维度扫描) + 响应 (S→C)
            NETWORK.registerMessage(12, MaidListQueryPacket.class,
                    MaidListQueryPacket::encode,
                    MaidListQueryPacket::decode,
                    MaidListQueryPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
            NETWORK.registerMessage(13, MaidListResponsePacket.class,
                    MaidListResponsePacket::encode,
                    MaidListResponsePacket::decode,
                    MaidListResponsePacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            LOGGER.info("[LMA] 网络通道初始化完成 (12 packets)");

            // 配置屏幕注册已移至 LmaForgeClientEntry (客户端入口 — 专用服务器无 Screen 类)
        });
    }
//?} else {
//?}

//? if 1.20.1 {
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MoreActionAPI.loadServerDurations();
        LOGGER.info("[LMA] 服务端动画数据加载完成");
    }
//?}
}
