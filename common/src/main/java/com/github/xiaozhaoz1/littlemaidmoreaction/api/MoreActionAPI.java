package com.github.xiaozhaoz1.littlemaidmoreaction.api;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
//? if 1.20.1 {
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
//?} else {
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
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

/**
 * 模组核心 API (v79.48 瘦身) — 仅保留客户端资源重载监听器。
 *
 * <p>v79.48: 12 委托 + findMaidById 全删 (0 引用实证) — 3 活方法 (loadServerDurations/
 * registerCustomAnimations/scanCustomAnimations) 调用方已改直调
 * {@link AnimationDurationManager} / {@link AnimationResourceRegistrar}。
 * 委托存根历史: v7 拆分后仅剩此监听器。
 */
//? if 1.20.1 {
@Mod.EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
//?} else {
@EventBusSubscriber(modid = LittleMaidMoreAction.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
//?}
public final class MoreActionAPI {

    // ======================== 资源重载 ========================

    @SubscribeEvent
    public static void onClientReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((barrier, rm, prep, reload, back, game) ->
                barrier.wait(null).thenRunAsync(() -> {
                    // v72 Phase 5: 规则引擎退役 — reload 仅重载动画/音效
                    com.github.xiaozhaoz1.littlemaidmoreaction.storage.StartupLoader.reload();
                    var animRes = com.github.xiaozhaoz1.littlemaidmoreaction.resource.DynamicAnimationResources.instance;
                    if (animRes != null) animRes.reload();
                    AnimationDurationManager.loadClientDurations();
                    try {
                        com.github.tartaricacid.touhoulittlemaid.client.resource.CustomPackLoader.reloadPacks();
                    } catch (Exception e) {
                        LittleMaidMoreAction.LOGGER.warn("[LMA] reloadPacks: {}", e.getMessage());
                    }
                }, game));
    }
}
