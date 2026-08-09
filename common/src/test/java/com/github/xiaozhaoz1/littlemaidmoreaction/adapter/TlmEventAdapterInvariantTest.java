package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TlmEventAdapter 不变量测试 (v72 Phase 5: 规则引擎已删除, 重接 = 编译失败)。
 *
 * <p>断言: 仅保留 2 个真实现 (@SubscribeEvent) — 任务系统真实现守护。
 */
class TlmEventAdapterInvariantTest {

    @Test
    @DisplayName("仅 2 个 @SubscribeEvent 订阅者 (任务系统真实现)")
    void onlyTwoSubscribeHandlers() {
        // 测试跑在 forge 节点 — 注解类是 forge 版 (net.minecraftforge.eventbus.api.SubscribeEvent)
        long count = 0;
        for (Method m : TlmEventAdapter.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(net.minecraftforge.eventbus.api.SubscribeEvent.class)) {
                count++;
            }
        }
        assertEquals(2, count, "事件链裁撤后应仅剩 onMaidTaskEnable + onEntityJoin");
    }

    // v72 Phase 5: noHandleEventCalls 用例已删 — 规则引擎类已删除, 重接 = 编译失败, 无需反射守护

    @Test
    @DisplayName("保留的两个订阅者是任务系统真实现")
    void keptHandlers_areTaskImplementations() throws Exception {
        Method enable = TlmEventAdapter.class.getDeclaredMethod("onMaidTaskEnable",
            Class.forName("com.github.tartaricacid.touhoulittlemaid.api.event.MaidTaskEnableEvent"));
        Method join = TlmEventAdapter.class.getDeclaredMethod("onEntityJoin",
            net.minecraftforge.event.entity.EntityJoinLevelEvent.class);
        assertNotNull(enable);
        assertNotNull(join);
    }
}
