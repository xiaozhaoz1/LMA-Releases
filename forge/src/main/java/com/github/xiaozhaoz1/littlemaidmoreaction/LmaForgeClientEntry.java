package com.github.xiaozhaoz1.littlemaidmoreaction;

import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.AiControlConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.AiControlConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BellRingConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.BellRingConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.BlockInteractConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.BlockInteractConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.CraftChainConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.CraftChainConfigScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.ItemListConfigMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.screen.ItemListConfigScreen;
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
        // 配置屏工厂 — 客户端类加载期注册 (服务器不加载本类); v79.51: 打开入口收敛 ScreenRegistry "lma_config"
        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory(
                    (mc, parent) -> com.github.xiaozhaoz1.littlemaidmoreaction.screen.ScreenRegistry
                            .create("lma_config", parent)));
    }

    /** v79.62.3: 防御塔方块实体渲染 — 自绘 DefenseTowerRenderer (泛型绑定 DefenseGarageKitBlockEntity, 修复 1.21.1 BER 泛型不匹配不渲染) */
    @SubscribeEvent
    public static void registerRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlockEntityTypes.GARAGE_KIT_DEFENSE.get(),
                com.github.xiaozhaoz1.littlemaidmoreaction.defense.tower.DefenseTowerRenderer::new);
    }

    /** 菜单屏注册 — commonSetup 期 (RegistryObject.get() 需注册冻结后) */
    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // v79.63 (A4): 选区调试的木棒判定由客户端注入 (vanilla 层不得 import event.StickBindUtil)
            com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator.bindStickCheck(
                    stack -> com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StickBindUtil.isMarkItem(stack)
                            || com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StickBindUtil.isBindItem(stack));
            // javac 交集边界 (Screen & MenuAccess<M>) 推断失败 → 显式类型参数
            // 2026-09-21 表驱动 (用户批准): 屏侧唯一事实源 = com.github.xiaozhaoz1.littlemaidmoreaction.client.GuiScreenBindings.DEFS ✓
            //   原 8 条手写注册 (含 FQN 汤) ⇒ 1 个循环 ✓; 菜单侧类型化字段与其 257 处消费者**保持原样** ✓
            var cfgMenus = java.util.Map.<String, net.minecraft.world.inventory.MenuType<?>>of(
                    "block_interact_config", LittleMaidMoreAction.BLOCK_INTERACT_CONFIG_MENU.get(),
                    "item_list_config", LittleMaidMoreAction.ITEM_LIST_CONFIG_MENU.get(),
                    "craft_chain_config", LittleMaidMoreAction.CRAFT_CHAIN_CONFIG_MENU.get(),
                    "bell_ring_config", LittleMaidMoreAction.BELL_RING_CONFIG_MENU.get(),
                    "dam_fill_config", LittleMaidMoreAction.DAM_FILL_CONFIG_MENU.get(),
                    "void_excavation_config", LittleMaidMoreAction.VOID_EXCAVATION_CONFIG_MENU.get(),
                    "ai_control_config", LittleMaidMoreAction.AI_CONTROL_CONFIG_MENU.get(),
                    "defense_tower", LittleMaidMoreAction.DEFENSE_TOWER_MENU.get());
            for (com.github.xiaozhaoz1.littlemaidmoreaction.client.GuiScreenBindings.Binding<?, ?> b : com.github.xiaozhaoz1.littlemaidmoreaction.client.GuiScreenBindings.DEFS) {
                MenuScreens.register((net.minecraft.world.inventory.MenuType) cfgMenus.get(b.id()),
                        (MenuScreens.ScreenConstructor) com.github.xiaozhaoz1.littlemaidmoreaction.client.GuiScreenBindings.screenOf(b));
            }
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
        // v79.61x: PatPat 抚摸反应 — 轮询 PatPat 状态表 (PatPat 按键取消服务端交互事件, 只能客户端检测)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ClientTickEvent ev) ->
                        com.github.xiaozhaoz1.littlemaidmoreaction.compat.patpat.PatPatReactionClient.onClientTick());
        // v79.65: 防御塔手办动作 (YSM 通道) — 瞄准循环 + 开火触发; 原生 gecko 由 LmaMagicCastingProvider 走 ISS
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ClientTickEvent ev) -> {
                    if (ev.phase == net.minecraftforge.event.TickEvent.Phase.END) {
                        com.github.xiaozhaoz1.littlemaidmoreaction.defense.client.TowerStatueAnimationDriver
                                .onClientTick();
                    }
                });
        // M-3: 客户端断开 → 清 MaidListResponsePacket 静态缓存 (防跨世界 stale 列表) + PatPat 客户端节流表
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut ev) -> {
                        com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidListResponsePacket.clearCache();
                        com.github.xiaozhaoz1.littlemaidmoreaction.compat.patpat.PatPatReactionClient.clearCache();
                        com.github.xiaozhaoz1.littlemaidmoreaction.defense.client.TowerStatueAnimationDriver.clearCache();
                        com.github.xiaozhaoz1.littlemaidmoreaction.defense.client.YsmAnimAvailability.clearCache();
                        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator.clear();
                });
        // v79.51 (KeyTrigger): 通用按键触发 — MOD bus 注册全部绑定 (选项→控制 可重绑) + GAME bus 检测
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (net.minecraftforge.client.event.RegisterKeyMappingsEvent ev) ->
                        com.github.xiaozhaoz1.littlemaidmoreaction.client.MaidKeyTriggerClient
                                .getAllBindings().forEach(ev::register));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.InputEvent.Key ev) ->
                        com.github.xiaozhaoz1.littlemaidmoreaction.client.MaidKeyTriggerClient.handleKeyInput());
        // v79.61x 选区高亮调试 (用户裁定, 落位 execute) — 渲染帧 (AFTER_BLOCK_ENTITIES) +
        // 潜行木棒右键标记 (客户端 cancel 阻止服务端事件 → 与既有 BlockInteract/ArmTransfer 标记零冲突)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderLevelStageEvent ev) -> {
                    if (ev.getStage() == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
                        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator
                                .render(ev.getPoseStack(), ev.getCamera(),
                                        net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource());
                    }
                });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock ev) -> {
                    if (!ev.getLevel().isClientSide()) return;
                    if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator
                            .isSelectionClick(ev.getItemStack(), ev.getEntity().isShiftKeyDown())) return;
                    // 2026-08-16: 不 setCanceled — 纯观察, 事件照常流向服务端 (用户裁定)
                    com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator
                            .advance(ev.getLevel(), ev.getPos());
                });
        // v79.62 统一容器绑定菜单 — 标记物品(默认木棍, 非潜行)右键容器 → 打开角色菜单 (客户端开屏, 防原版容器 GUI)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock ev) -> {
                    if (!ev.getLevel().isClientSide()) return;
                    if (ev.getEntity().isShiftKeyDown()) return; // shift 留给选区标记
                    if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StickBindUtil.isMarkItem(ev.getItemStack())) return;
                    if (!com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StickBindUtil.isContainer(ev.getLevel(), ev.getPos())) return;
                    net.minecraft.client.Minecraft.getInstance().setScreen(
                            new com.github.xiaozhaoz1.littlemaidmoreaction.screen.FarmContainerScreen(ev.getPos()));
                    ev.setCanceled(true);
                    ev.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                });
        // v79.62.5 锻造任务已删 (用户裁定) — 原 smithing 右键打开 GUI listener 移除
        // v79.62 区域制绑定: 木棍 + 已有选区 + 右键女仆 → 发 FarmRegionBindPacket (选区 AABB 到服务端建区域)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract ev) -> {
                    if (!ev.getLevel().isClientSide()) return;
                    if (!(ev.getTarget() instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) return;
                    var player = ev.getEntity();
                    if (!(com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StickBindUtil.isMarkItem(player.getMainHandItem())
                            || com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.world.StickBindUtil.isBindItem(player.getMainHandItem()))) return;
                    // v79.63: 记住"当前交付目标" ⇒ 之后右键容器时菜单只列该任务需要的角色 (用户裁定 B)
                    com.github.xiaozhaoz1.littlemaidmoreaction.screen.MarkTarget.set(
                            com.github.xiaozhaoz1.littlemaidmoreaction.adapter.LmaTaskTypeRegistry
                                    .extractTaskType(maid.getTask().getUid().getPath()));
                    // 仅在木棍已标记种子源/目标箱 (farm 区域绑定模式) 时发 — 避免与 arm_transfer 交付冲突
                    net.minecraft.nbt.CompoundTag heldTag = player.getMainHandItem().getTag();
                    if (heldTag == null
                            || (!heldTag.contains("farm_seed") && !heldTag.contains("farm_harvest"))) return;
                    net.minecraft.world.phys.AABB region = com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator
                            .getRegionAABB();
                    if (region == null) return;
                    com.github.xiaozhaoz1.littlemaidmoreaction.network.FarmRegionBindPacket.sendToServer(
                            maid.getStringUUID(), region);
                });
        // 选区尺寸 HUD (屏幕层)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre ev) ->
                        com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.DebugSelectionCoordinator
                                .drawHud(ev.getGuiGraphics()));
        // v79.50: 图鉴屏打开注入 (包字节码禁 Screen — DEDICATED_SERVER RuntimeDistCleaner 实证)
        // v79.51: 赋值点保留 (boot 期就绪), 实现收敛 ScreenRegistry.openCodex
        com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidCodexScreenPacket.opener =
                com.github.xiaozhaoz1.littlemaidmoreaction.screen.ScreenRegistry::openCodex;
    }
}
