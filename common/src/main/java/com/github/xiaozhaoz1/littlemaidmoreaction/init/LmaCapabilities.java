package com.github.xiaozhaoz1.littlemaidmoreaction.init;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;

/**
 * v79.62.3 防御塔容器能力注册 (漏斗供弹/补武器).
 *
 * <p>双平台差异:
 * - 1.20.1 (forge): BE 覆写 {@code getCapability} (CapabilityProvider 模式) → 本类 no-op;
 * - 1.21.1 (neoforge): mod 总线 {@code RegisterCapabilitiesEvent}
 *   {@code registerBlockEntity(Capabilities.ItemHandler.BLOCK, TYPE, provider)}.
 */
public final class LmaCapabilities {

    private LmaCapabilities() {}

//? if 1.20.1 {
    /** 1.20.1: 无注册 — BE getCapability 覆写已提供 (CapabilityProvider 链) */
    public static void register(net.minecraftforge.eventbus.api.IEventBus modBus) {
    }
//?} else {
    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(LmaCapabilities::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.github.xiaozhaoz1.littlemaidmoreaction.init.LmaBlockEntityTypes.GARAGE_KIT_DEFENSE.get(),
                (be, dir) -> be.getItemHandler());
    }
//?}
}
