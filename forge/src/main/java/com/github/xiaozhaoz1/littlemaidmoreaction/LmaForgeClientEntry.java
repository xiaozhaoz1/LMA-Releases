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
            // v79.62.2: 填坝排水配置 (排水开关)
            MenuScreens.<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigMenu,
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigScreen>register(
                LittleMaidMoreAction.DAM_FILL_CONFIG_MENU.get(),
                (menu, inv, title) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.DamFillConfigScreen(menu, inv, title));
            // v79.62: 挖空置域区块数配置
            MenuScreens.<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigMenu,
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigScreen>register(
                LittleMaidMoreAction.VOID_EXCAVATION_CONFIG_MENU.get(),
                (menu, inv, title) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.VoidExcavationConfigScreen(menu, inv, title));
            MenuScreens.<AiControlConfigMenu, AiControlConfigScreen>register(
                LittleMaidMoreAction.AI_CONTROL_CONFIG_MENU.get(),
                (menu, inv, title) -> new AiControlConfigScreen(menu, inv, title));
            // v79.62.1: 锻造容器界面 (原版锻造台样式, 无模板)
            MenuScreens.<com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.MaidSmithingMenu,
                    com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.MaidSmithingScreen>register(
                LittleMaidMoreAction.SMITHING_MENU.get(),
                (menu, inv, title) -> new com.github.xiaozhaoz1.littlemaidmoreaction.task.gui.MaidSmithingScreen(menu, inv, title));
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
        // M-3: 客户端断开 → 清 MaidListResponsePacket 静态缓存 (防跨世界 stale 列表) + PatPat 客户端节流表
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut ev) -> {
                        com.github.xiaozhaoz1.littlemaidmoreaction.network.MaidListResponsePacket.clearCache();
                        com.github.xiaozhaoz1.littlemaidmoreaction.compat.patpat.PatPatReactionClient.clearCache();
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
                    if (!com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isMarkItem(ev.getItemStack())) return;
                    if (!com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isContainer(ev.getLevel(), ev.getPos())) return;
                    net.minecraft.client.Minecraft.getInstance().setScreen(
                            new com.github.xiaozhaoz1.littlemaidmoreaction.screen.FarmContainerScreen(ev.getPos()));
                    ev.setCanceled(true);
                    ev.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                });
        // v79.62 锻造: 右键女仆 + smithing 任务 + 附近锻造台 → 打开锻造升级界面
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract ev) -> {
                    if (!ev.getLevel().isClientSide()) return;
                    if (!(ev.getTarget() instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) return;
                    var held = ev.getEntity().getMainHandItem();
                    if (com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isMarkItem(held)
                            || com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isBindItem(held)) return;
                    String task = com.github.xiaozhaoz1.littlemaidmoreaction.task.data.FlowTaskData.getTask(maid);
                    if (!"smithing".equals(task)) return;
                    if (com.github.xiaozhaoz1.littlemaidmoreaction.task.pipeline.SmithingPipeline
                            .findSmithingTable((net.minecraft.server.level.ServerLevel) maid.level(), maid) == null) return;
                    // v79.62.1: 打开 TLM 任务设置标签页 → TaskConfigGuiFactory 返回锻造容器界面 (原版锻造台样式, 无模板)
                    com.github.tartaricacid.touhoulittlemaid.network.NetworkHandler.CHANNEL.sendToServer(
                            new com.github.tartaricacid.touhoulittlemaid.network.message.OpenMaidGuiMessage(
                                    maid.getId(), com.github.tartaricacid.touhoulittlemaid.entity.passive.TabIndex.TASK_CONFIG));
                    ev.setCanceled(true);
                    ev.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                });
        // v79.62 区域制绑定: 木棍 + 已有选区 + 右键女仆 → 发 FarmRegionBindPacket (选区 AABB 到服务端建区域)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract ev) -> {
                    if (!ev.getLevel().isClientSide()) return;
                    if (!(ev.getTarget() instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) return;
                    var player = ev.getEntity();
                    if (!(com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isMarkItem(player.getMainHandItem())
                            || com.github.xiaozhaoz1.littlemaidmoreaction.event.StickBindUtil.isBindItem(player.getMainHandItem()))) return;
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
