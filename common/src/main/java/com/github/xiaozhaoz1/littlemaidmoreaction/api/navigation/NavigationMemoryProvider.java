package com.github.xiaozhaoz1.littlemaidmoreaction.api.navigation;

import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.core.BlockPos;

import java.util.function.Supplier;

/**
 * 导航 Memory 类型供应接口 (2026-09-21 接口倒置 — 清 {@code api → adapter} 反向依赖)。
 *
 * <p>背景: {@code NavigationMemory} 原直接 import {@code adapter.LmaMemoryModuleRegistry} 取
 * {@code MemoryModuleType} holder, 形成「对外契约层 → TLM 桥接层」的**层倒置**。
 * 现改由实现方 (adapter) 在注册期注入供应器, api 只依赖本接口。
 *
 * <p><b>为什么返回 Supplier 而不是取值后的类型</b>: 注入发生在 mod 构造期 (早于注册事件),
 * 此时 DeferredRegister 的 holder 还没解析出值 ⇒ 接口必须传**延迟取值器**,
 * 由调用方在**运行期** (女仆 Brain 实际读写时) 才解析; 这也是项目统一纪律
 * 「共享 holder/supplier, 使用点取值」(见 init/README 时序表) 的同一条做法。
 *
 * <p>实现方: {@code adapter.LmaMemoryModuleRegistry} (forge = {@code RegistryObject},
 * neoforge = {@code DeferredHolder}, 二者均 implements {@code Supplier})。
 */
public interface NavigationMemoryProvider {

    /** 导航目标方块坐标 (无 Codec ⇒ 不持久化 ⇒ 卸载自动清除) */
    Supplier<MemoryModuleType<BlockPos>> navTarget();

    /** 导航启动 tick (超时检测用) */
    Supplier<MemoryModuleType<Long>> navStartTick();
}
