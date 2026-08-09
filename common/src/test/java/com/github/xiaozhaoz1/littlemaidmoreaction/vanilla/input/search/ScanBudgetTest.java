package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.input.search;

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
        for (int i = 0; i < 10; i++) b.trySectionScan();
        b.refresh(5);   // 同 tick — 不重置
        assertEquals(ScanBudget.MAX_SECTION_SCANS_PER_TICK - 10, b.sectionsRemaining());
    }

    @Test
    @DisplayName("新 tick refresh 重置池")
    void refresh_newTick_resets() {
        ScanBudget b = new ScanBudget();
        b.resetForTick(5);
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
        int ok = 0;
        while (b.tryChunkLoad()) ok++;
        assertEquals(ScanBudget.MAX_CHUNK_LOADS_PER_TICK, ok);
    }

    @Test
    @DisplayName("检查池独立 (128/tick)")
    void checks_limited() {
        ScanBudget b = new ScanBudget();
        b.resetForTick(1);
        int ok = 0;
        while (b.tryCheck()) ok++;
        assertEquals(ScanBudget.MAX_CHECKS_PER_TICK, ok);
    }
}
