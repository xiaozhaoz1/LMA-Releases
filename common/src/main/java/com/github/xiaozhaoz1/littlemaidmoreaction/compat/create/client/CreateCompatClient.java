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

    /** 便携装配 Screen 绑定 (双平台 — 1.21 MenuScreens.register 私有, 改 RegisterMenuScreensEvent) */
//? if 1.20.1 {
    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        // v79.63 修**装配屏不注册 (用户实机: 打开装配任务什么都没弹 + 原界面消失)**
        //   物理前提 = **菜单类型已注册** (它本身只在 create 在场时注册), 与开关查询/时序无关;
        //   旧写法用 CompatToggle+ModList 作前提 ⇒ 若任一处判假就**静默少一个屏工厂** ⇒
        //   原版 MenuScreens 只报 "Failed to create screen for menu type" (用户看到的"界面不见了")。
        // ★ 直取 DeferredRegister Supplier (已绑定) — **不依赖 LmaMenus 注入时序**
        //   (与 LmaNeoForgeClientEntry.Screens 同款; 装配屏此前是唯一漏改的屏 ⇒ 注入未完成时
        //    register(null,...) 静默落在 null 键上 ⇒ 打开时报 "Failed to create screen")
        var assemblyMenu = LittleMaidMoreAction.MAID_ASSEMBLY_MENU.get();
        if (assemblyMenu != null) {
            event.enqueueWork(() -> MenuScreens.register(
                assemblyMenu, MaidAssemblyScreen::new));
            if (!com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")) {
                LittleMaidMoreAction.LOGGER.warn("[MaidAssembly] 装配屏已注册, 但 create 模块开关为关 — 菜单类型仍在 (toggles 与实际注册不一致?)");
            }
        } else {
            // v79.63: 门控不成立原来完全静默 — 用户视角="装配菜单开了但没有界面"
            // 契约: 任务注册门控 (TaskRegistryManifest.CREATE) 必须与本处同条件, 否则任务在而屏缺
            LittleMaidMoreAction.LOGGER.warn("[MaidAssembly] 装配屏未注册 (forge) — 菜单类型为 null (create 未注册/未加载); create模块启用={} create已加载={}; 装配GUI将无界面",
                    com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create"),
                    net.minecraftforge.fml.ModList.get().isLoaded("create"));
        }
    }
//?} else {
    @SubscribeEvent
    public static void onRegisterScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        // v79.63 修**装配屏不注册** (同 forge 分支): 前提改为"菜单类型已注册" (物理前提, 与时序无关)
        // ★ 直取 DeferredRegister Supplier (已绑定) — 不依赖 LmaMenus 注入时序 (同 forge 分支说明)
        var assemblyMenu = com.github.xiaozhaoz1.littlemaidmoreaction.LmaNeoForgeEntry.MAID_ASSEMBLY_MENU.get();
        if (assemblyMenu != null) {
            event.register(assemblyMenu, MaidAssemblyScreen::new);
            if (!com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create")) {
                LittleMaidMoreAction.LOGGER.warn("[MaidAssembly] 装配屏已注册, 但 create 模块开关为关 — 菜单类型仍在 (toggles 与实际注册不一致?)");
            }
        } else {
            // v79.63: 同 forge — 门控不成立必须留痕, 别让 GUI 静默消失
            LittleMaidMoreAction.LOGGER.warn("[MaidAssembly] 装配屏未注册 (neoforge) — 菜单类型为 null (create 未注册/未加载); create模块启用={} create已加载={}; 装配GUI将无界面",
                    com.github.xiaozhaoz1.littlemaidmoreaction.compat.CompatToggle.isModuleEnabled("create"),
                    net.neoforged.fml.ModList.get().isLoaded("create"));
        }
    }
//?}
}
