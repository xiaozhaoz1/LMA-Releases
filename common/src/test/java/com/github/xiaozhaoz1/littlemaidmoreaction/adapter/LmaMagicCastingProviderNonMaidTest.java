package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code LmaMagicCastingProvider} 的"非女仆 ❌ {@code IMaid}"守卫单测（错题 #366 ✓）
 *
 * <p><b>为什么需要它</b>：真实触发场景是 **星之魔女（Stellar Witch）** Boss ——
 * 它来自其它模组（Spell 系）且前置一堆（铁魔法 等 ✗）⇒ 为一个崩溃去装整套前置不划算 ✓。
 * 但崩溃的**本质与那个 Boss 无关** ✗：TLM 的 {@link IMaid} 是**接口** ✓，被"非女仆的实体"实现 ✓
 * ⇒ 只要造一个**不是女仆的 IMaid** ✓ 就能等价复现 ✓✓
 *
 * <p><b>怎么造</b>：用 {@link Proxy} 动态代理 ✓ —— `IMaid` 的方法很多，逐个实现既啰嗦又易随 TLM 升级失效 ✗；
 * 代理只需在 {@code asEntity()} 上返回**非 EntityMaid**（这里用 null 表示"不是女仆" ✓，
 * 与 {@code instanceof} 判定等价 ✓）⇒ 无需加载任何第三方模组 ✓、无需 Minecraft 世界 ✓。
 *
 * <p><b>断言</b>：两个入口都必须**返回 null**（交回 TLM 默认逻辑 ✓），而**不是抛异常** ✗。
 * 若哪天有人把守卫删掉、又恢复 {@code (EntityMaid) maid.asEntity()} 的盲转 ✗ ⇒ 本测试会立刻红 ✓
 * （配合 {@code arch/IMaidDowncastGuardTest} 的静态扫描 ⇒ 动静双保险 ✓）。
 */
class LmaMagicCastingProviderNonMaidTest {

    /** 伪造"实现了 IMaid、但不是女仆"的实体 —— 等价于星之魔女那种第三方 Boss ✓ */
    private static IMaid fakeNonMaidIMaid() {
        return (IMaid) Proxy.newProxyInstance(
                IMaid.class.getClassLoader(),
                new Class<?>[]{IMaid.class},
                (proxy, method, methodArgs) -> {
                    if ("asEntity".equals(method.getName())) {
                        return null;      // ★ 非女仆 ✓（instanceof EntityMaid 为 false ⇒ 守卫生效 ✓）
                    }
                    return null;          // 其余方法本测试不会走到（守卫会在最前面返回 ✓）
                });
    }

    @Test
    @DisplayName("非女仆 IMaid ⇒ getMagicCastingState 返回 null（不抛 ClassCastException）")
    void getMagicCastingStateReturnsNullForNonMaid() {
        LmaMagicCastingProvider provider = new LmaMagicCastingProvider();
        IMaid notAMaid = fakeNonMaidIMaid();
        assertNull(provider.getMagicCastingState(notAMaid),
                "守卫失效 ✗：非女仆 IMaid 必须返回 null，交回 TLM 默认逻辑 ✓（错题 #366）");
    }

    @Test
    @DisplayName("非女仆 IMaid ⇒ getAnimationBuilder 返回 null")
    void getAnimationBuilderReturnsNullForNonMaid() {
        LmaMagicCastingProvider provider = new LmaMagicCastingProvider();
        IMaid notAMaid = fakeNonMaidIMaid();
        assertNull(provider.getAnimationBuilder(notAMaid, null),
                "守卫失效 ✗：非女仆 IMaid 必须返回 null ✓（错题 #366）");
    }
}
