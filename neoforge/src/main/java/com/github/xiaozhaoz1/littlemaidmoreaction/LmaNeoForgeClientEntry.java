package com.github.xiaozhaoz1.littlemaidmoreaction;

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
        // v79.51: 打开入口收敛 ScreenRegistry "lma_config"
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer1, parent) -> com.github.xiaozhaoz1.littlemaidmoreaction.screen.ScreenRegistry
                        .create("lma_config", parent));
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
        // v79.61x: PatPat 抚摸反应 — 轮询 PatPat 状态表 (PatPat 按键取消服务端交互事件, 只能客户端检测)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                event -> com.github.xiaozhaoz1.littlemaidmoreaction.compat.patpat.PatPatReactionClient.onClientTick());
        // M-3: 客户端断开 → 清 MaidListResponsePacket 静态缓存 (防跨世界 stale 列表; LoggingOut 是具体类, 可监听)
        //      + PatPat 客户端节流表
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class,
                event -> {
                    com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidListResponsePacket.clearCache();
                    com.github.xiaozhaoz1.littlemaidmoreaction.compat.patpat.PatPatReactionClient.clearCache();
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator.clear();
                });
        // v79.51 (KeyTrigger): 通用按键触发 — 注册全部绑定 (MOD bus, RegisterKeyMappingsEvent 是 IModBusEvent)
        // + 检测 (GAME bus 手动 — v79.18 教训: neoforge GAME bus 静态订阅失效, 禁 @EventBusSubscriber)
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent.class,
                event -> com.github.xiaozhaoz1.littlemaidmoreaction.client.MaidKeyTriggerClient
                        .getAllBindings().forEach(event::register));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.InputEvent.Key.class,
                event -> com.github.xiaozhaoz1.littlemaidmoreaction.client.MaidKeyTriggerClient.handleKeyInput());
        // v79.61x 选区高亮调试 (用户裁定, 落位 execute) — 渲染帧 (AFTER_BLOCK_ENTITIES) +
        // 潜行木棒右键标记。2026-08-16 用户裁定: 不 cancel 事件 (纯读取 pos, 不影响原版/其他
        // mod 右键流 — 曾 cancel 导致用户困惑右键被拦截); 服务端标记照常 (Shift+右键本就允许)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.RenderLevelStageEvent.class,
                event -> {
                    if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
                        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator
                                .render(event.getPoseStack(), event.getCamera(),
                                        net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource());
                    }
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class,
                event -> {
                    if (!event.getLevel().isClientSide()) return;
                    if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator
                            .isSelectionClick(event.getItemStack(), event.getEntity().isShiftKeyDown())) return;
                    // 2026-08-16: 不 setCanceled — 纯观察, 事件照常流向服务端 (用户裁定)
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator
                            .advance(event.getLevel(), event.getPos());
                });
        // v79.62 统一容器绑定菜单 — 标记物品(默认木棍, 非潜行)右键容器 → 打开角色菜单 (客户端开屏, 防原版容器 GUI)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class,
                event -> {
                    if (!event.getLevel().isClientSide()) return;
                    if (event.getEntity().isShiftKeyDown()) return; // shift 留给选区标记
                    if (!com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isMarkItem(event.getItemStack())) return;
                    if (!com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isContainer(event.getLevel(), event.getPos())) return;
                    net.minecraft.client.Minecraft.getInstance().setScreen(
                            new com.github.xiaozhaoz1.littlemaidmoreaction.screen.FarmContainerScreen(event.getPos()));
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                });
        // 选区尺寸 HUD (屏幕层) — v79.61x 修复: RenderGuiEvent 是抽象类, NeoForge 禁止直接监听
        // (0.9.60 崩溃根因); 子类经 javap neoforge-21.1.247-universal.jar 实证只有 Pre/Post (无 Layer —
        // 首修猜 Layer 又编译失败, #243 补记), 用 Post (HUD 绘制后叠加尺寸文本)
        // v79.62 锻造: 右键女仆 + smithing 任务 + 附近锻造台 → 打开锻造升级界面
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class,
                event -> {
                    if (!event.getLevel().isClientSide()) return;
                    if (!(event.getTarget() instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid2)) return;
                    var held2 = event.getEntity().getMainHandItem();
                    if (com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isMarkItem(held2)
                            || com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isBindItem(held2)) return;
                    String task2 = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(maid2);
                    if (!"smithing".equals(task2)) return;
                    if (com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.SmithingPipeline
                            .findSmithingTable((net.minecraft.server.level.ServerLevel) maid2.level(), maid2) == null) return;
                    // v79.62.1: 打开 TLM 任务设置标签页 → TaskConfigGuiFactory 返回锻造容器界面 (原版锻造台样式, 无模板)
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new com.github.tartaricacid.touhoulittlemaid.network.message.OpenMaidGuiPackage(
                                    maid2.getId(), com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex.TASK_CONFIG));
                    event.setCanceled(true);
                    event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class,
                event -> {
                    if (!event.getLevel().isClientSide()) return;
                    if (!(event.getTarget() instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) return;
                    var player = event.getEntity();
                    if (!(com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isMarkItem(player.getMainHandItem())
                            || com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isBindItem(player.getMainHandItem()))) return;
                    // 仅在木棍已标记种子源/目标箱 (farm 区域绑定模式) 时发 — 避免与 arm_transfer 交付冲突
                    var heldCd = player.getMainHandItem().getOrDefault(
                            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            net.minecraft.world.item.component.CustomData.EMPTY);
                    net.minecraft.nbt.CompoundTag heldTag = heldCd.copyTag();
                    if ((!heldTag.contains("farm_seed") && !heldTag.contains("farm_harvest"))) return;
                    net.minecraft.world.phys.AABB region = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator
                            .getRegionAABB();
                    if (region == null) return;
                    com.github.xiaozhaoz1.littlemaidmoreaction.network.FarmRegionBindPacket.sendToServer(
                            maid.getStringUUID(), region);
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.RenderGuiEvent.Post.class,
                event -> com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator
                        .drawHud(event.getGuiGraphics()));
        // v79.50: 图鉴屏打开注入 (包字节码禁 Screen — DEDICATED_SERVER RuntimeDistCleaner 实证)
        // v79.51: 赋值点保留 (boot 期就绪), 实现收敛 ScreenRegistry.openCodex
        com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidCodexScreenPacket.opener =
                com.github.xiaozhaoz1.littlemaidmoreaction.screen.ScreenRegistry::openCodex;
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
            // v79.62.2: 填坝排水配置 (排水开关)
            event.<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigMenu,
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigScreen>register(
                    LmaNeoForgeEntry.DAM_FILL_CONFIG_MENU.get(),
                    (menu, inv, title) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigScreen(menu, inv, title));
            // v79.62: 挖空置域区块数配置
            event.<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigMenu,
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigScreen>register(
                    LmaNeoForgeEntry.VOID_EXCAVATION_CONFIG_MENU.get(),
                    (menu, inv, title) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigScreen(menu, inv, title));
            event.<AiControlConfigMenu, AiControlConfigScreen>register(LmaNeoForgeEntry.AI_CONTROL_CONFIG_MENU.get(), (menu, inv, title) -> new AiControlConfigScreen(menu, inv, title));
            // v79.62.1: 锻造容器界面 (原版锻造台样式, 无模板)
            event.<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.MaidSmithingMenu,
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.MaidSmithingScreen>register(
                    LmaNeoForgeEntry.SMITHING_MENU.get(),
                    (menu, inv, title) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.MaidSmithingScreen(menu, inv, title));
        }

    }
}
