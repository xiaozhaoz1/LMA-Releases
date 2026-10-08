package com.github.xiaozhaoz1.littlemaidmoreaction.task.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskStateMachine 泛型状态机引擎纯函数测试 (v79.50) — 转换校验 + 状态解析容错。
 *
 * <p>纯 JVM 铁律: 仅测引擎内 {@code static} 纯函数 (isTransitionAllowed/parseState) —
 * 类静态字段仅 String 常量, 方法 descriptor 全 JDK 类型, 不触发 EntityMaid/ServerLevel/
 * MaidData/LittleMaidMoreAction 类加载 (tick/readState 等 MC 耦合方法不被调用)。
 *
 * <p>覆盖边界说明:
 * <ul>
 *   <li>终态清理 (cleanup→clearState→MaidData.removePl) — EntityMaid 强耦合, 不可纯 JVM 测</li>
 *   <li>超时判断 — 不在本类, 在 GameTickPipelineManager (WatchdogMath.isTimedOut,
 *       已由 WatchdogMathTest 覆盖)</li>
 * </ul>
 */
class TaskStateMachineTest {

    /** 测试用状态枚举 */
    private enum TestState { IDLE, RUNNING, DONE, FAILED }

    // ── 转换合法性校验 ──

    @Test
    @DisplayName("图内转换放行: IDLE→RUNNING 在图中")
    void isTransitionAllowed_inGraph_allowed() {
        // Arrange
        Map<TestState, Set<TestState>> transitions = Map.of(
                TestState.IDLE, Set.of(TestState.RUNNING),
                TestState.RUNNING, Set.of(TestState.DONE));

        // Act
        boolean allowed = TaskStateMachine.isTransitionAllowed(TestState.IDLE, TestState.RUNNING, transitions);

        // Assert
        assertTrue(allowed);
    }

    @Test
    @DisplayName("非法转换拦截: IDLE→DONE 不在图中")
    void isTransitionAllowed_notInGraph_rejected() {
        // Arrange
        Map<TestState, Set<TestState>> transitions = Map.of(
                TestState.IDLE, Set.of(TestState.RUNNING));

        // Act
        boolean allowed = TaskStateMachine.isTransitionAllowed(TestState.IDLE, TestState.DONE, transitions);

        // Assert
        assertFalse(allowed);
    }

    @Test
    @DisplayName("空图不限制: transitions() 默认 Map.of() 任意转换放行")
    void isTransitionAllowed_emptyGraph_anyAllowed() {
        // Arrange/Act/Assert — 空图 (轻量级使用) 无任何约束
        assertTrue(TaskStateMachine.isTransitionAllowed(TestState.IDLE, TestState.DONE, Map.of()));
        assertTrue(TaskStateMachine.isTransitionAllowed(TestState.FAILED, TestState.RUNNING, Map.of()));
    }

    @Test
    @DisplayName("current 无出边拦截: 图中未登记的状态不可出发转换")
    void isTransitionAllowed_currentWithoutOutEdge_rejected() {
        // Arrange
        Map<TestState, Set<TestState>> transitions = Map.of(
                TestState.IDLE, Set.of(TestState.RUNNING));

        // Act
        boolean allowed = TaskStateMachine.isTransitionAllowed(TestState.DONE, TestState.IDLE, transitions);

        // Assert
        assertFalse(allowed);
    }

    @Test
    @DisplayName("自环转换放行: 循环状态在图中")
    void isTransitionAllowed_selfLoop_allowed() {
        // Arrange
        Map<TestState, Set<TestState>> transitions = Map.of(
                TestState.RUNNING, Set.of(TestState.RUNNING, TestState.DONE));

        // Act
        boolean allowed = TaskStateMachine.isTransitionAllowed(TestState.RUNNING, TestState.RUNNING, transitions);

        // Assert
        assertTrue(allowed);
    }

    // ── 状态解析容错 (readState 防 NBT 损坏) ──

    @Test
    @DisplayName("合法状态名解析: RUNNING → 对应枚举")
    void parseState_validName_returnsEnum() {
        // Act
        TestState state = TaskStateMachine.parseState(TestState.class, "RUNNING", TestState.IDLE);

        // Assert
        assertEquals(TestState.RUNNING, state);
    }

    @Test
    @DisplayName("空状态名 → fallback 初始状态")
    void parseState_emptyName_returnsFallback() {
        // Act
        TestState state = TaskStateMachine.parseState(TestState.class, "", TestState.IDLE);

        // Assert
        assertEquals(TestState.IDLE, state);
    }

    @Test
    @DisplayName("未知状态名 (NBT 损坏) → fallback 初始状态")
    void parseState_unknownName_returnsFallback() {
        // Act
        TestState state = TaskStateMachine.parseState(TestState.class, "GARBAGE_STATE", TestState.IDLE);

        // Assert
        assertEquals(TestState.IDLE, state);
    }

    @Test
    @DisplayName("null 状态名 → fallback 初始状态")
    void parseState_nullName_returnsFallback() {
        // Act
        TestState state = TaskStateMachine.parseState(TestState.class, null, TestState.IDLE);

        // Assert
        assertEquals(TestState.IDLE, state);
    }

    @Test
    @DisplayName("stateClass() 返回 null → fallback 不炸 (防御)")
    void parseState_nullClass_returnsFallback() {
        // Act — 子类 stateClass() 误返回 null 时不得抛异常
        TestState state = TaskStateMachine.parseState(null, "RUNNING", TestState.IDLE);

        // Assert
        assertEquals(TestState.IDLE, state);
    }
}
