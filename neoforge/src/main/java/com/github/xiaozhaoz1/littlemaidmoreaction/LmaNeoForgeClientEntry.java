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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge 1.21.1 客户端入口 — 菜单屏幕绑定 + 配置屏扩展点。
 * <p>对应 forge 侧 MenuScreens.register (4 个配置屏)。
 * v75: 删 NumenCompanionRender (假人可见 — YSM 模型) + NumenSpeechMirror (无女仆气泡对象)。</p>
 */
@Mod(value = LittleMaidMoreAction.MOD_ID, dist = Dist.CLIENT)
public final class LmaNeoForgeClientEntry {

    public LmaNeoForgeClientEntry(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer1, parent) -> new LMAConfigScreen(parent));
        // v79.18 修复: neoforge @EventBusSubscriber(GAME) auto-scan 实测失效 (ISS 动画事件收不到 —
        // 日志: TLM "Model loading time" 出现但 LMA "注册 N 个动画到 TLM" 缺失, cachedIISSFile 恒 null)
        // → 构造器手动注册到 GAME 总线 (TLM 1.5.3 反编译实证: post 到 NeoForge.EVENT_BUS)
        // ⚠ 禁 MOD 总线注册 — DefaultGeckoAnimationEvent 非 IModBusEvent, addListener 直接抛
        // IllegalStateException (实测崩溃 14:42; 文档"MOD 总线"为 forge 时代旧说法, 不适用于 neoforge)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent.class,
                event -> com.github.xiaozhaoz1.littlemaidmoreaction.api.AnimationResourceRegistrar
                        .registerCustomAnimations(event));
        com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction.LOGGER.info(
                "[LMA/Registrar] GAME bus DefaultGeckoAnimationEvent listener 已手动注册 (构造器)");
        // v79.18: 客户端资源重载 listener — YSM 注入 + ISS 热合并 (RegisterClientReloadListenersEvent 是 IModBusEvent → MOD bus 注册合法)
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent.class,
                event -> event.registerReloadListener(new com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm.YsmReloadListener()));
        // v79.18: tick 延迟补全 — TLM 模型异步加载晚于 reload listener (ClientTickEvent.Post 是具体类, 可监听)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                event -> com.github.xiaozhaoz1.littlemaidmoreaction.compat.ysm.YsmReloadListener.onClientTick());
        // v79.20.4c: 构造期注入已删 — mod 并行构造与 YSM builtin 解压竞态 (NoSuchFileException 崩溃, 用户实测);
        // 注入时机 = YsmReloadListener.prepare (资源重载, 所有 construct 完成后 YSM 包已就绪)
    }

    @EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Screens {
        @SubscribeEvent
        public static void clientSetup(RegisterMenuScreensEvent event) {
            // 直取 DeferredRegister Supplier (已绑定) — 不依赖 LmaMenus 注入时序
            event.<BlockInteractConfigMenu, BlockInteractConfigScreen>register(LmaNeoForgeEntry.BLOCK_INTERACT_CONFIG_MENU.get(), (menu, inv, title) -> new BlockInteractConfigScreen(menu, inv, title));
            event.<ItemListConfigMenu, ItemListConfigScreen>register(LmaNeoForgeEntry.ITEM_LIST_CONFIG_MENU.get(), (menu, inv, title) -> new ItemListConfigScreen(menu, inv, title));
            event.<CraftChainConfigMenu, CraftChainConfigScreen>register(LmaNeoForgeEntry.CRAFT_CHAIN_CONFIG_MENU.get(), (menu, inv, title) -> new CraftChainConfigScreen(menu, inv, title));
            event.<BellRingConfigMenu, BellRingConfigScreen>register(LmaNeoForgeEntry.BELL_RING_CONFIG_MENU.get(), (menu, inv, title) -> new BellRingConfigScreen(menu, inv, title));
            event.<AiControlConfigMenu, AiControlConfigScreen>register(LmaNeoForgeEntry.AI_CONTROL_CONFIG_MENU.get(), (menu, inv, title) -> new AiControlConfigScreen(menu, inv, title));
        }

    }
}
