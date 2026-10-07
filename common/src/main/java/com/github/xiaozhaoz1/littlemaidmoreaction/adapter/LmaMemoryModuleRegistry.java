package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import com.github.xiaozhaoz1.littlemaidmoreaction.LittleMaidMoreAction;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationMemory;
import com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation.NavigationMemoryProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.function.Supplier;
//? if 1.20.1 {
import net.minecraftforge.eventbus.api.IEventBus;
//?} else {
import net.neoforged.bus.api.IEventBus;
//?}
//? if 1.20.1 {
import net.minecraftforge.registries.DeferredRegister;
//?} else {
import net.neoforged.neoforge.registries.DeferredRegister;
//?}
//? if 1.20.1 {
import net.minecraftforge.registries.RegistryObject;
//?}

import java.util.Optional;

/**
 * LMA 自定义 MemoryModuleType 注册中心 (v12.7 P0)。
 *
 * <p>导航目标使用 Brain Memory — 不跨会话持久化，Activity 切换时自动清除。
 *
 * <p><b>2026-09-21 接口倒置</b>: 本类实现 {@link NavigationMemoryProvider} (api 侧接口),
 * 在 {@link #register} 里把自身注入 {@link NavigationMemory} ⇒ 清掉
 * 「api → adapter」反向依赖 (原 3 处, 另 2 处随 {@code LittleMaidMoreActionExtension} 迁入 init 已解)。
 */
public final class LmaMemoryModuleRegistry implements NavigationMemoryProvider {

    /** provider 单例 (取已注册的 holder; 平台类型经 Supplier 上浮) */
    private static final NavigationMemoryProvider PROVIDER = new LmaMemoryModuleRegistry();


    public static final DeferredRegister<MemoryModuleType<?>> REGISTER =
            DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, LittleMaidMoreAction.MOD_ID);

    /** 导航目标方块坐标 (无 Codec → 不持久化 → 女仆卸载自动清除) */
//? if 1.20.1 {
    public static final RegistryObject<MemoryModuleType<BlockPos>> NAV_TARGET =
//?} else {
    public static final Supplier<MemoryModuleType<BlockPos>> NAV_TARGET =
//?}
            REGISTER.register("lma_nav_target",
                    () -> new MemoryModuleType<>(Optional.empty()));

    /** 导航启动 tick — 用于超时检测 */
//? if 1.20.1 {
    public static final RegistryObject<MemoryModuleType<Long>> NAV_START_TICK =
//?} else {
    public static final Supplier<MemoryModuleType<Long>> NAV_START_TICK =
//?}
            REGISTER.register("lma_nav_start_tick",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
        installNavigationMemory();
    }

    /** 把本类注入 api 侧 {@link NavigationMemory} (接口倒置: api 不再反向 import adapter) */
    public static void installNavigationMemory() {
        NavigationMemory.install(PROVIDER);
    }

    @Override
    public Supplier<MemoryModuleType<BlockPos>> navTarget() {
        return NAV_TARGET;
    }

    @Override
    public Supplier<MemoryModuleType<Long>> navStartTick() {
        return NAV_START_TICK;
    }

    private LmaMemoryModuleRegistry() {}
}
