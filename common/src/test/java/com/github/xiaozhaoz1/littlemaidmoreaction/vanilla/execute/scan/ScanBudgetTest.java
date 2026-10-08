package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScanBudget 纯 JVM 测试 (v77.5) — 预算池消耗/重置/tick 注入。
 */
class ScanBudgetTest {

    @Test
    @DisplayName("池消耗: section 扫描 256 次后耗尽")
    void sectionScan_exhaustsAtLimit() {
        ScanBudget b = new ScanBudget();
        b.resetForTick(1);
        b.overrideDeadlineForTest(Long.MAX_VALUE);   // 隔离墙钟门 ⇒ 本断言只测池计数 (确定性)
        int ok = 0;
        while (b.trySectionScan()) ok++;
        assertEquals(ScanBudget.MAX_SECTION_SCANS_PER_TICK, ok);
        assertFalse(b.trySectionScan());
    }

    @Test
    @DisplayName("同 tick 重复 refresh 不重置 (冻结 tick 语义)")
    void refresh_sameTick_noReset() {
        ScanBudget b = new ScanBudget();
        b.resetForTick(5);
        b.overrideDeadlineForTest(Long.MAX_VALUE);   // 隔离墙钟门 (本断言只测"同 tick 冻结"语义)
        for (int i = 0; i < 10; i++) b.trySectionScan();
        b.refresh(5);   // 同 tick — 不重置
        assertEquals(ScanBudget.MAX_SECTION_SCANS_PER_TICK - 10, b.sectionsRemaining());
    }

    @Test
    @DisplayName("新 tick refresh 重置池")
    void refresh_newTick_resets() {
        ScanBudget b = new ScanBudget();
        b.resetForTick(5);
        b.overrideDeadlineForTest(Long.MAX_VALUE);   // 隔离墙钟门 (本断言只测"新 tick 重置"语义)
        for (int i = 0; i < ScanBudget.MAX_SECTION_SCANS_PER_TICK; i++) b.trySectionScan();
        assertEquals(0, b.sectionsRemaining());
        b.refresh(6);   // 新 tick — 重置
        assertEquals(ScanBudget.MAX_SECTION_SCANS_PER_TICK, b.sectionsRemaining());
    }

    @Test
    @DisplayName("区块加载预算独立且有限 (2/tick)")
    void chunkLoad_limited() {
        ScanBudget b = new ScanBudget();
        b.resetForTick(1);
        b.overrideDeadlineForTest(Long.MAX_VALUE);   // 隔离墙钟门 ⇒ 只测区块加载池 (2/tick)
        int ok = 0;
        while (b.tryChunkLoad()) ok++;
        assertEquals(ScanBudget.MAX_CHUNK_LOADS_PER_TICK, ok);
    }

    @Test
    @DisplayName("检查池独立 (128/tick)")
    void checks_limited() {
        ScanBudget b = new ScanBudget();
        b.resetForTick(1);
        b.overrideDeadlineForTest(Long.MAX_VALUE);   // 隔离墙钟门 ⇒ 只测检查池 (128/tick)
        int ok = 0;
        while (b.tryCheck()) ok++;
        assertEquals(ScanBudget.MAX_CHECKS_PER_TICK, ok);
    }

    @Test
    @DisplayName("墙钟门: 已过期时即使池满也一律拒绝 (确定性, 不靠赛跑)")
    void deadline_gate_blocksEvenWithFullPool() {
        ScanBudget b = new ScanBudget();
        b.resetForTick(1);
        b.overrideDeadlineForTest(System.nanoTime() - 1);   // 注入"刚过期" (必须在 reset 之后)
        assertFalse(b.trySectionScan());
        assertFalse(b.tryCheck());
        assertFalse(b.tryChunkLoad());
        // 池未动 ⇒ 证明拒绝来自**时间门**而非池空
        assertEquals(ScanBudget.MAX_SECTION_SCANS_PER_TICK, b.sectionsRemaining());
    }
}
