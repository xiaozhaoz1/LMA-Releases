package com.github.xiaozhaoz1.littlemaidmoreaction;

import com.github.xiaozhaoz1.littlemaidmoreaction.screen.LMAConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BellRingConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BellRingConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BlockInteractConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BlockInteractConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.CraftChainConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.CraftChainConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.ItemListConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.ItemListConfigScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Forge 客户端注册入口 — 专用服务器无 Screen 类, 客户端注册全部集中于此。
 *
 * <p>{@code @Mod.EventBusSubscriber(value = Dist.CLIENT)} — FML 仅客户端注册本类订阅者,
 * 服务器不加载本类 (RuntimeDistCleaner 类转换级检查: 含 Screen 字节码引用的类
 * 不可在 dedicated server 加载)。对应 neoforge 侧 {@code LmaNeoForgeClientEntry}。
 */
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LmaForgeClientEntry {

    static {
        // 配置屏工厂 — 客户端类加载期注册 (服务器不加载本类)
        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory(
                    (mc, parent) -> new LMAConfigScreen(parent)));
    }

    /** 菜单屏注册 — commonSetup 期 (RegistryObject.get() 需注册冻结后) */
    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // javac 交集边界 (Screen & MenuAccess<M>) 推断失败 → 显式类型参数
            MenuScreens.<BlockInteractConfigMenu, BlockInteractConfigScreen>register(
                LittleMaidMoreAction.BLOCK_INTERACT_CONFIG_MENU.get(),
                (menu, inv, title) -> new BlockInteractConfigScreen(menu, inv, title));
            MenuScreens.<ItemListConfigMenu, ItemListConfigScreen>register(
                LittleMaidMoreAction.ITEM_LIST_CONFIG_MENU.get(),
                (menu, inv, title) -> new ItemListConfigScreen(menu, inv, title));
            MenuScreens.<CraftChainConfigMenu, CraftChainConfigScreen>register(
                LittleMaidMoreAction.CRAFT_CHAIN_CONFIG_MENU.get(),
                (menu, inv, title) -> new CraftChainConfigScreen(menu, inv, title));
            MenuScreens.<BellRingConfigMenu, BellRingConfigScreen>register(
                LittleMaidMoreAction.BELL_RING_CONFIG_MENU.get(),
                (menu, inv, title) -> new BellRingConfigScreen(menu, inv, title));
            MenuScreens.<AiControlConfigMenu, AiControlConfigScreen>register(
                LittleMaidMoreAction.AI_CONTROL_CONFIG_MENU.get(),
                (menu, inv, title) -> new AiControlConfigScreen(menu, inv, title));
            // v79.20.4c: commonSetup 期注入已删 — mod 并行加载与 YSM builtin 解压竞态 (崩溃, 用户实测);
            // 注入时机 = YsmReloadListener.prepare (资源重载, 所有 mod 加载完成后 YSM 包已就绪)
        });
        // v79.18: 客户端资源重载 listener — YSM 注入 + ISS 热合并 (重载 prepare 时 YSM 文件已生成)
        // forge IEventBus 无 (Class, Consumer) 重载 — 显式 lambda 参数类型让泛型推断
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (net.minecraftforge.client.event.RegisterClientReloadListenersEvent ev) ->
                        ev.registerReloadListener(new com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm.YsmReloadListener()));
        // v79.18: tick 延迟补全 — TLM 模型异步加载晚于 reload listener (forge 1.20.1 = TickEvent.ClientTickEvent)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ClientTickEvent ev) ->
                        com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm.YsmReloadListener.onClientTick());
    }
}
