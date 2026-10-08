package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ToolJudge 纯逻辑层单测 (v79.57 抽层) — 布尔组合/查表判定直测。
 * 纯 JVM: 无 MC 类型出入参 (错题 #174/#173 铁律 — MC IO 门面不可测, 纯函数可测)。
 */
class ToolJudgeLogicTest {

    // ── matchesToolTypeFlags: 方块类型 → 工具匹配 ──

    @Test
    void matchesToolTypeFlags_镐类型_仅镐匹配() {
        assertTrue(ToolJudge.matchesToolTypeFlags(true, false, false, ToolJudge.ToolType.PICKAXE));
        assertFalse(ToolJudge.matchesToolTypeFlags(false, true, false, ToolJudge.ToolType.PICKAXE));
        assertFalse(ToolJudge.matchesToolTypeFlags(false, false, true, ToolJudge.ToolType.PICKAXE));
    }

    @Test
    void matchesToolTypeFlags_斧类型_仅斧匹配() {
        assertTrue(ToolJudge.matchesToolTypeFlags(false, true, false, ToolJudge.ToolType.AXE));
        assertFalse(ToolJudge.matchesToolTypeFlags(true, false, false, ToolJudge.ToolType.AXE));
        assertFalse(ToolJudge.matchesToolTypeFlags(false, false, true, ToolJudge.ToolType.AXE));
    }

    @Test
    void matchesToolTypeFlags_铲类型_仅铲匹配() {
        assertTrue(ToolJudge.matchesToolTypeFlags(false, false, true, ToolJudge.ToolType.SHOVEL));
        assertFalse(ToolJudge.matchesToolTypeFlags(true, false, false, ToolJudge.ToolType.SHOVEL));
    }

    @Test
    void matchesToolTypeFlags_无类型要求_恒真() {
        // 草/花/液体等 NONE — 任意工具可挖
        assertTrue(ToolJudge.matchesToolTypeFlags(false, false, false, ToolJudge.ToolType.NONE));
        assertTrue(ToolJudge.matchesToolTypeFlags(true, true, true, ToolJudge.ToolType.NONE));
    }

    // ── isModeOptimalFlags: 模式最优判断 (ORE 镐/铲可用, WOOD 斧) ──

    @Test
    void isModeOptimalFlags_ORE_镐可用_最优() {
        assertTrue(ToolJudge.isModeOptimalFlags(true, false, false, true, true));
    }

    @Test
    void isModeOptimalFlags_ORE_铲可用_最优_铲豁免() {
        // 泥土/沙属采集目标 — 手拿好铲不换 (防镐↔铲抖动)
        assertTrue(ToolJudge.isModeOptimalFlags(false, true, false, true, true));
    }

    @Test
    void isModeOptimalFlags_ORE_镐不可用_非最优() {
        // 耐久不足 (isToolUsable false) → 需要换
        assertFalse(ToolJudge.isModeOptimalFlags(true, false, false, false, true));
    }

    @Test
    void isModeOptimalFlags_ORE_剑斧_非最优() {
        // 拿剑/斧 → 全矿被 canHarvest 过滤 → 必须换镐
        assertFalse(ToolJudge.isModeOptimalFlags(false, false, true, true, true));
        assertFalse(ToolJudge.isModeOptimalFlags(false, false, false, true, true));
    }

    @Test
    void isModeOptimalFlags_WOOD_斧_最优_无可用检查() {
        // 旧字节码同构: WOOD 只查 isAxe, 不查 usable
        assertTrue(ToolJudge.isModeOptimalFlags(false, false, true, false, false));
        assertFalse(ToolJudge.isModeOptimalFlags(true, false, false, true, false));
    }

    // ── intervalTicksForTier: 破坏间隔查表 ──

    @Test
    void intervalTicksForTier_各等级查表() {
        // 木20 / 石15 / 铁10 / 钻5 / 下界合金5 (用户 2026-07-17 定义)
        assertEquals(20, ToolJudge.intervalTicksForTier(0, true, true));
        assertEquals(15, ToolJudge.intervalTicksForTier(1, true, true));
        assertEquals(10, ToolJudge.intervalTicksForTier(2, true, true));
        assertEquals(5, ToolJudge.intervalTicksForTier(3, true, true));
        assertEquals(5, ToolJudge.intervalTicksForTier(4, true, true));
    }

    @Test
    void intervalTicksForTier_tier越界_clamp到表尾() {
        assertEquals(5, ToolJudge.intervalTicksForTier(99, true, true));
    }

    @Test
    void intervalTicksForTier_非对应工具_空手_将坏_慢速兜底() {
        // 非对应类型工具 (铲挖矿) → 40
        assertEquals(40, ToolJudge.intervalTicksForTier(3, false, true));
        // 工具将坏 (usable false) → 40
        assertEquals(40, ToolJudge.intervalTicksForTier(3, true, false));
        // 空手 (tier < 0) → 40
        assertEquals(40, ToolJudge.intervalTicksForTier(-1, false, true));
    }
}
