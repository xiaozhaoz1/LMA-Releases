package com.github.xiaozhaoz1.littlemaidmoreaction.vanilla.execute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MaidChainState 单测 — 跳过集行为 (tier 分组/容量淘汰/过期) + per-maid 隔离。
 * 纯 JVM: 不触碰 lastMode 字段 (MC 枚举类型, 惰性不加载)。
 */
class MaidChainStateTest {

    private static final int SKIP_MAX = 10;
    private static final int SKIP_TTL = 60;

    @Test
    void maintainTier_工具等级变化_清空跳过集与时间戳() {
        // Arrange
        MaidChainState st = new MaidChainState();
        st.addSkip(1L, 10L, SKIP_MAX);
        st.addSkip(2L, 11L, SKIP_MAX);
        // Act
        st.maintainTier(3); // 原 tierLevel = MIN_VALUE → 变化
        // Assert
        assertTrue(st.skipped.isEmpty());
        assertTrue(st.skipAt.isEmpty());
        assertEquals(3, st.tierLevel);
    }

    @Test
    void maintainTier_同等级_保留跳过集() {
        // Arrange
        MaidChainState st = new MaidChainState();
        st.maintainTier(3);
        st.addSkip(1L, 10L, SKIP_MAX);
        // Act
        st.maintainTier(3);
        // Assert
        assertEquals(1, st.skipped.size());
        assertTrue(st.skipped.contains(1L));
    }

    @Test
    void addSkip_容量满_淘汰最旧() {
        // Arrange
        MaidChainState st = new MaidChainState();
        for (long i = 1; i <= SKIP_MAX; i++) {
            st.addSkip(i, i * 10L, SKIP_MAX);
        }
        // Act
        st.addSkip(99L, 1000L, SKIP_MAX);
        // Assert
        assertEquals(SKIP_MAX, st.skipped.size());
        assertFalse(st.skipped.contains(1L));  // 最旧被淘汰
        assertNull(st.skipAt.get(1L));         // 时间戳连带清
        assertTrue(st.skipped.contains(99L));
    }

    @Test
    void addSkip_未满_直接添加() {
        // Arrange
        MaidChainState st = new MaidChainState();
        // Act
        st.addSkip(5L, 50L, SKIP_MAX);
        // Assert
        assertEquals(1, st.skipped.size());
        assertEquals(Long.valueOf(50L), st.skipAt.get(5L));
    }

    @Test
    void expire_过期_移除时间戳并返回true() {
        // Arrange
        MaidChainState st = new MaidChainState();
        st.addSkip(1L, 100L, SKIP_MAX);
        // Act
        boolean expired = st.expire(1L, 100L + SKIP_TTL + 1, SKIP_TTL);
        // Assert
        assertTrue(expired);
        assertNull(st.skipAt.get(1L));  // 时间戳已清 (调用方 removeIf 负责从 skipped 移除)
    }

    @Test
    void expire_未过期_返回false() {
        // Arrange
        MaidChainState st = new MaidChainState();
        st.addSkip(1L, 100L, SKIP_MAX);
        // Act
        boolean expired = st.expire(1L, 100L + SKIP_TTL - 1, SKIP_TTL);
        // Assert
        assertFalse(expired);
        assertEquals(Long.valueOf(100L), st.skipAt.get(1L));
    }

    @Test
    void addSkip_同位置重复_刷新时间戳_永不过期() {
        // 文档化 (错题 #184): addSkip 覆写时间戳 — 调用方必须只在首次失败时调用,
        // 否则每 tick 刷新 → expire 永 false → 目标永久跳过 (P1 根因: tryStartVein
        // 原对已跳过目标每 tick addSkip; v79.56 改 firstFail 门控)
        MaidChainState st = new MaidChainState();
        st.addSkip(1L, 100L, SKIP_MAX);
        st.addSkip(1L, 500L, SKIP_MAX); // 模拟每 tick 刷新
        // Assert: 以刷新后时间戳为基准 — 59 < 60 未过期; 61 > 60 才过期
        assertFalse(st.expire(1L, 559L, SKIP_TTL));
        assertTrue(st.expire(1L, 561L, SKIP_TTL));
        // 若调用方只在首次失败记 (不刷新) — 100 为基准, 差 61 > 60 即过期 (条件是严格大于)
        MaidChainState st2 = new MaidChainState();
        st2.addSkip(1L, 100L, SKIP_MAX);
        assertTrue(st2.expire(1L, 161L, SKIP_TTL));
    }

    @Test
    void addSkip_失败计数递增_上限3() {
        // v79.56 退避: 连续失败计数, 上限 MAX_FAIL=3 (第 3 次升级长 TTL)
        MaidChainState st = new MaidChainState();
        st.addSkip(1L, 100L, SKIP_MAX);
        assertEquals(Integer.valueOf(1), st.failCounts.get(1L));
        st.addSkip(1L, 200L, SKIP_MAX);
        assertEquals(Integer.valueOf(2), st.failCounts.get(1L));
        st.addSkip(1L, 300L, SKIP_MAX);
        st.addSkip(1L, 400L, SKIP_MAX);
        // Assert: 上限 3 不无限膨胀
        assertEquals(Integer.valueOf(3), st.failCounts.get(1L));
    }

    @Test
    void ttlFor_失败3次_升级长TTL() {
        // 用户裁定: 第 1 次 60t / 第 3 次 600t = 30 秒封顶, 不卡住
        MaidChainState st = new MaidChainState();
        st.addSkip(1L, 100L, SKIP_MAX);
        assertEquals(SKIP_TTL, st.ttlFor(1L, SKIP_TTL));       // 1 次 → 短 TTL
        st.addSkip(1L, 200L, SKIP_MAX);
        st.addSkip(1L, 300L, SKIP_MAX);
        assertEquals(MaidChainState.TTL_LONG, st.ttlFor(1L, SKIP_TTL)); // 3 次 → 600t
    }

    @Test
    void expire_短TTL过期_保留失败计数() {
        // 计数 <3 过期 → 计数保留 (累积到 3 次升级)
        MaidChainState st = new MaidChainState();
        st.addSkip(1L, 100L, SKIP_MAX);
        // Act: 60t 后过期 (差 61 > 60)
        assertTrue(st.expire(1L, 161L, SKIP_TTL));
        // Assert: 时间戳清, 计数保留 (下次 FAILED 再 +1)
        assertNull(st.skipAt.get(1L));
        assertEquals(Integer.valueOf(1), st.failCounts.get(1L));
    }

    @Test
    void expire_长TTL过期_重置失败计数() {
        // 计数 ≥3 → 600t 后才过期, 过期时计数重置 (新周期)
        MaidChainState st = new MaidChainState();
        st.addSkip(1L, 100L, SKIP_MAX);
        st.addSkip(1L, 200L, SKIP_MAX);
        st.addSkip(1L, 300L, SKIP_MAX);
        // 60t 内不过期 (长 TTL 生效)
        assertFalse(st.expire(1L, 350L, SKIP_TTL));
        // 600t 后过期 → 计数清 (重置)
        assertTrue(st.expire(1L, 300L + MaidChainState.TTL_LONG + 1, SKIP_TTL));
        assertNull(st.failCounts.get(1L));
    }

    @Test
    void maintainTier_清空失败计数() {
        MaidChainState st = new MaidChainState();
        st.addSkip(1L, 100L, SKIP_MAX);
        st.addSkip(1L, 200L, SKIP_MAX);
        // Act: 换工具 (tier 变化) → 全清
        st.maintainTier(3);
        // Assert
        assertTrue(st.skipped.isEmpty());
        assertTrue(st.skipAt.isEmpty());
        assertTrue(st.failCounts.isEmpty());
    }

    @Test
    void expire_时间戳为零_返回false() {
        // Arrange
        MaidChainState st = new MaidChainState();
        // Act
        boolean expired = st.expire(1L, 100L, SKIP_TTL);
        // Assert
        assertFalse(expired);
    }

    @Test
    void perMaid隔离_两实例互不影响() {
        // Arrange
        MaidChainState a = new MaidChainState();
        MaidChainState b = new MaidChainState();
        a.addSkip(1L, 10L, SKIP_MAX);
        b.addSkip(2L, 20L, SKIP_MAX);
        // Act
        a.maintainTier(3); // a 清空 (原 MIN_VALUE → 3)
        // Assert
        assertTrue(a.skipped.isEmpty());
        assertEquals(1, b.skipped.size()); // b 不受 a 影响 (原全局 SKIP_AT 会误删的根因场景)
        assertTrue(b.skipped.contains(2L));
        assertEquals(Long.valueOf(20L), b.skipAt.get(2L));
    }
}
