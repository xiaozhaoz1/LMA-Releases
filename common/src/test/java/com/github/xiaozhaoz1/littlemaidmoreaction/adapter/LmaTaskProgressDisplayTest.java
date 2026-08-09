package com.github.xiaozhaoz1.littlemaidmoreaction.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link LmaTaskProgressDisplay} 语义映射纯函数测试 (v79.21)。
 *
 * <p>friendlyName/stateName 为 package-private — 同包测试直接访问。
 */
class LmaTaskProgressDisplayTest {

    @Test
    @DisplayName("任务类型 → 友好中文名 (树内 12 任务全映射)")
    void friendlyName_knownTask_mapped() {
        assertEquals("配方链合成", LmaTaskProgressDisplay.friendlyName("craft_chain"));
        assertEquals("熔炉烧炼", LmaTaskProgressDisplay.friendlyName("furnace"));
        assertEquals("唱片机", LmaTaskProgressDisplay.friendlyName("jukebox"));
        assertEquals("搬运", LmaTaskProgressDisplay.friendlyName("arm_transfer"));
        assertEquals("手摇曲柄", LmaTaskProgressDisplay.friendlyName("crank"));
        assertEquals("连锁砍树", LmaTaskProgressDisplay.friendlyName("chain_wood"));
        assertEquals("连锁挖矿", LmaTaskProgressDisplay.friendlyName("chain_ore"));
        assertEquals("便携装配", LmaTaskProgressDisplay.friendlyName("maid_assembly"));
        assertEquals("方块交互", LmaTaskProgressDisplay.friendlyName("block_interact"));
        assertEquals("怪物日志", LmaTaskProgressDisplay.friendlyName("monster_log"));
    }

    @Test
    @DisplayName("未知任务类型 → 回退原文")
    void friendlyName_unknownTask_fallback() {
        assertEquals("some_task", LmaTaskProgressDisplay.friendlyName("some_task"));
    }

    @Test
    @DisplayName("null/空任务类型 → 未知任务")
    void friendlyName_nullOrEmpty_fallback() {
        assertEquals("未知任务", LmaTaskProgressDisplay.friendlyName(null));
        assertEquals("未知任务", LmaTaskProgressDisplay.friendlyName(""));
    }

    @Test
    @DisplayName("FSM 状态枚举名 → 中文 (18 状态覆盖)")
    void stateName_knownState_mapped() {
        assertEquals("寻找目标", LmaTaskProgressDisplay.stateName("SEARCHING"));
        assertEquals("前往途中", LmaTaskProgressDisplay.stateName("NAVIGATING"));
        assertEquals("砍伐中", LmaTaskProgressDisplay.stateName("CHOPPING"));
        assertEquals("摇动曲柄", LmaTaskProgressDisplay.stateName("CRANKING"));
        assertEquals("工作中", LmaTaskProgressDisplay.stateName("WORKING"));
        assertEquals("提供动力", LmaTaskProgressDisplay.stateName("POWERING"));
        assertEquals("前往取物", LmaTaskProgressDisplay.stateName("TO_TAKE"));
        assertEquals("取物中", LmaTaskProgressDisplay.stateName("TAKING"));
        assertEquals("前往存放", LmaTaskProgressDisplay.stateName("TO_DEPOSIT"));
        assertEquals("存放中", LmaTaskProgressDisplay.stateName("DEPOSITING"));
        assertEquals("待机", LmaTaskProgressDisplay.stateName("IDLE"));
        assertEquals("尝试启动", LmaTaskProgressDisplay.stateName("TRY_START"));
        assertEquals("推进", LmaTaskProgressDisplay.stateName("ADVANCE"));
        assertEquals("敲击中", LmaTaskProgressDisplay.stateName("STRIKE"));
        assertEquals("进食复位", LmaTaskProgressDisplay.stateName("EAT_RESET"));
        assertEquals("等待", LmaTaskProgressDisplay.stateName("WAITING"));
        assertEquals("移动", LmaTaskProgressDisplay.stateName("MOVE"));
        assertEquals("注视", LmaTaskProgressDisplay.stateName("LOOK"));
    }

    @Test
    @DisplayName("未知状态 → 回退原文")
    void stateName_unknownState_fallback() {
        assertEquals("FOO", LmaTaskProgressDisplay.stateName("FOO"));
    }

    @Test
    @DisplayName("null/空状态 → 工作中 (通用兜底)")
    void stateName_nullOrEmpty_fallback() {
        assertEquals("工作中", LmaTaskProgressDisplay.stateName(null));
        assertEquals("工作中", LmaTaskProgressDisplay.stateName(""));
    }
}
