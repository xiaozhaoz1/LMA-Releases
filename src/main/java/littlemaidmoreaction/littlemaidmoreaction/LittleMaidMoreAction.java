package littlemaidmoreaction.littlemaidmoreaction;

import littlemaidmoreaction.littlemaidmoreaction.api.MoreActionAPI;
import littlemaidmoreaction.littlemaidmoreaction.config.MoreActionConfig;
import littlemaidmoreaction.littlemaidmoreaction.init.LmaRegistrar;
import littlemaidmoreaction.littlemaidmoreaction.network.LmaAnimSyncMessage;
import littlemaidmoreaction.littlemaidmoreaction.network.OpenMaidEditorMessage;
import littlemaidmoreaction.littlemaidmoreaction.screen.LMAConfigScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * 车万女仆「更多动作」附属模组。
 *
 * v3 规则引擎：18 条件 Key + 39 动作类型 + 25 事件 + JSON 预设 + 可视化编辑器。
 * 处决/闪避/弹反通过 startup.json 预设驱动，用户可自由扩展。
 *
 * 依赖：touhou_little_maid ≥ 1.5.0、Minecraft Forge 1.20.1
 */
@Mod(LittleMaidMoreAction.MOD_ID)
public final class LittleMaidMoreAction {
    public static final String MOD_ID = "littlemaidmoreaction";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 配置文件根目录: config/littlemaidmoreaction/ */
    public static final java.nio.file.Path CONFIG_DIR =
            net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve(MOD_ID);

    /**
     * 模组网络通道。
     *
     * 用于服务端→客户端同步数据，驱动动画渲染和 GUI 交互。
     */
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "network"),
            () -> "1.0",
            s -> s.equals("1.0"),
            s -> s.equals("1.0")
    );

    public LittleMaidMoreAction() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        // 注册配置 + GUI 入口
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MoreActionConfig.SPEC, MOD_ID + "-common.toml");
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new LMAConfigScreen(parent)));

        // v10: 核心初始化 (扫描/加载/文档) → LmaRegistrar
        LmaRegistrar.init();
        // v10: 服务端初始化 (任务执行器/文档/版本门控)
        LmaRegistrar.initServer();
        // v10: 音效注册 (DeferredRegister → LmaSounds)
        LmaRegistrar.registerSounds(modBus);
        // ★ v12.7 P0: MemoryModuleType 注册 (DeferredRegister → LmaMemoryModuleRegistry)
        LmaRegistrar.registerMemoryModules(modBus);
        // v10: TPM 事件由 TpmCompat.init() 统一注册 (Compat 模式)

        MinecraftForge.EVENT_BUS.register(this);
    }

    /** 注册网络包序列化器 */
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NETWORK.registerMessage(0, LmaAnimSyncMessage.class,
                    LmaAnimSyncMessage::encode,
                    LmaAnimSyncMessage::decode,
                    LmaAnimSyncMessage::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            NETWORK.registerMessage(1, OpenMaidEditorMessage.class,
                    OpenMaidEditorMessage::encode,
                    OpenMaidEditorMessage::decode,
                    OpenMaidEditorMessage::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            LOGGER.info("[LMA] 网络通道初始化完成 (2 packets)");
        });
    }

    /**
     * 服务端启动时加载动画时长和武器映射数据。
     *
     * 在单人游戏中，客户端加载的动画数据已通过配置文件同步；
     * 在专用服务器上，使用配置文件中的手动配置值。
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MoreActionAPI.loadServerDurations();
        LOGGER.info("[LMA] 服务端动画数据加载完成");
    }
}
