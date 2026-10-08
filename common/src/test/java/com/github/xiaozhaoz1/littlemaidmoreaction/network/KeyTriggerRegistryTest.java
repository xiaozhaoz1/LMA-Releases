package com.github.xiaozhaoz1.littlemaidmoreaction.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * KeyTriggerRegistry 纯 JVM 测试 (v79.51) — 注册表 map 行为。
 *
 * <p>纯 JVM 铁律: 只测 register/get 的 JDK map 行为 — handler 用匿名类 (非 lambda:
 * lambda 经 invokedynamic LambdaMetafactory 解析接口方法 descriptor 会触发
 * EntityMaid/ServerPlayer 类加载, 违反单测铁律; 匿名类加载只解析父类/接口, 惰性)。
 * 不调 {@code init()} (其 lambda 引用 TaskRegistry — MC 依赖), 不调用 handler 方法。
 */
class KeyTriggerRegistryTest {

    /** 匿名 handler — 不触发任何 MC 类加载 (方法体不被调用) */
    private static final KeyTriggerHandler NOOP = new KeyTriggerHandler() {
        @Override
        public void handle(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid,
                           net.minecraft.server.level.ServerPlayer player) {
            // 测试只验证注册表行为, 不调用
        }
    };

    @Test
    @DisplayName("注册后 get 返回同一 handler")
    void register_get_returnsSameHandler() {
        KeyTriggerRegistry.register("test_a", NOOP);
        assertSame(NOOP, KeyTriggerRegistry.get("test_a"));
    }

    @Test
    @DisplayName("重复 keyId 注册抛异常 (防静默覆盖)")
    void register_duplicate_throws() {
        KeyTriggerRegistry.register("test_dup", NOOP);
        assertThrows(IllegalArgumentException.class,
                () -> KeyTriggerRegistry.register("test_dup", NOOP));
    }

    @Test
    @DisplayName("未注册 keyId 返回 null (handle 层容错静默)")
    void get_unregistered_returnsNull() {
        assertNull(KeyTriggerRegistry.get("test_not_registered"));
    }

    @Test
    @DisplayName("不同 keyId 独立存储, 互不覆盖")
    void register_differentKeys_isolated() {
        KeyTriggerRegistry.register("test_x", NOOP);
        KeyTriggerRegistry.register("test_y", NOOP);
        assertSame(NOOP, KeyTriggerRegistry.get("test_x"));
        assertSame(NOOP, KeyTriggerRegistry.get("test_y"));
        assertNotNull(KeyTriggerRegistry.get("test_x"));
    }
}
