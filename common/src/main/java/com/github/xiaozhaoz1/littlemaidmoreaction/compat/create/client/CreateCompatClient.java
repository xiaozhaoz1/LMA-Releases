package com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.client;
import com.github.xiaozhaoz1.littlemaidmoreaction.LmaMenus;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyMenu;
import com.github.xiaozhaoz1.littlemaidmoreaction.compat.create.task.assembly.MaidAssemblyScreen;
import com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlockEntityTypes;
import net.minecraft.client.gui.screens.MenuScreens;
//? if 1.20.1 {
import net.minecraftforge.api.distmarker.Dist;
//?} else {
import net.neoforged.api.distmarker.Dist;
//?}
//? if 1.20.1 {
import net.minecraftforge.client.event.EntityRenderersEvent;
//?} else {
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?} else {
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.common.Mod;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
//?}
//? if 1.20.1 {
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
//?} else {
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//?}

/**
 * Create Compat 客户端注册 (v4.2 + v56).
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?}
public final class CreateCompatClient {

    private CreateCompatClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 无 Create 时 MAID_POWER_BELT 为 null (LmaBlocks 门控) — 跳过渲染注册
        if (LmaBlockEntityTypes.MAID_POWER_BELT == null) return;
        event.registerBlockEntityRenderer(
                LmaBlockEntityTypes.MAID_POWER_BELT.get(),
                MaidPowerBeltRenderer::new);
    }

    /** v56: 便携装配 Screen 绑定 (v75.1 双平台 — 1.21 MenuScreens.register 私有, 改 RegisterMenuScreensEvent) */
//? if 1.20.1 {
    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        // v77: CompatToggle 一致性门控 (模块关闭时菜单绑定无意义)
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")
                && net.minecraftforge.fml.ModList.get().isLoaded("create")) {
            event.enqueueWork(() -> MenuScreens.register(
                LmaMenus.MAID_ASSEMBLY_MENU, MaidAssemblyScreen::new));
        }
    }
//?} else {
    @SubscribeEvent
    public static void onRegisterScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        if (com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")
                && net.neoforged.fml.ModList.get().isLoaded("create")) {
            event.register(LmaMenus.MAID_ASSEMBLY_MENU, MaidAssemblyScreen::new);
        }
    }
//?}
}
