package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CooldownManager} 的单元测试。
 *
 * <p>测试冷却键格式和纯时间比较逻辑，不依赖 Minecraft EntityMaid。</p>
 */
class CooldownManagerTest {

    // ── 键格式 ──

    @Test
    @DisplayName("cooldown key format is lma_rule_<id>")
    void keyFormat() {
        // 验证前缀常量 — 与 PersistentData 中旧 RuleMatcher 兼容
        assertEquals("lma_rule_", CooldownManager.COOLDOWN_KEY_PREFIX);
    }

    // ── isExpired 正常路径 ──

    @Test
    @DisplayName("isExpired returns true when cooldown has passed")
    void isExpired_cooldownPassed() {
        // now=1000, lastUsed=800, cooldown=100 → 200 ticks elapsed ≥ 100 → expired
        assertTrue(CooldownManager.isExpired(1000, 800, 100));
    }

    @Test
    @DisplayName("isExpired returns false when cooldown has not passed")
    void isExpired_stillOnCooldown() {
        // now=1000, lastUsed=950, cooldown=100 → 50 ticks < 100 → still cooling
        assertFalse(CooldownManager.isExpired(1000, 950, 100));
    }

    @Test
    @DisplayName("isExpired returns true exactly at boundary")
    void isExpired_exactlyAtBoundary() {
        // now=1000, lastUsed=900, cooldown=100 → exactly 100 ticks → expired
        assertTrue(CooldownManager.isExpired(1000, 900, 100));
    }

    @Test
    @DisplayName("isExpired returns true one tick before boundary")
    void isExpired_oneTickBeforeBoundary() {
        // now=1000, lastUsed=901, cooldown=100 → 99 ticks < 100 → still cooling
        assertFalse(CooldownManager.isExpired(1000, 901, 100));
    }

    // ── 零值和边界 ──

    @Test
    @DisplayName("isExpired returns true when cooldown is zero")
    void isExpired_zeroCooldown() {
        assertTrue(CooldownManager.isExpired(1000, 999, 0));
    }

    @Test
    @DisplayName("isExpired returns true when cooldown is negative")
    void isExpired_negativeCooldown() {
        assertTrue(CooldownManager.isExpired(1000, 999, -1));
    }

    @Test
    @DisplayName("isExpired returns true when never triggered (lastUsed=0)")
    void isExpired_neverTriggered() {
        // 从未触发 → lastUsed=0 → 应该过期（允许首次触发）
        assertTrue(CooldownManager.isExpired(1000, 0, 100));
    }

    @Test
    @DisplayName("isExpired handles large cooldown values")
    void isExpired_largeCooldown() {
        // 12000 ticks = 10 minutes
        // lastUsed=1000, now=12999 → 11999 < 12000 → still cooling
        assertFalse(CooldownManager.isExpired(12999, 1000, 12000));
        // lastUsed=1000, now=13000 → 12000 >= 12000 → expired
        assertTrue(CooldownManager.isExpired(13000, 1000, 12000));
    }

    // ── tick 溢出保护 (Bug #13, #15 教训) ──

    @Test
    @DisplayName("isExpired returns true when lastUsed > now (tick overflow / stale data)")
    void isExpired_lastUsedGreaterThanNow() {
        // lastUsed > now → 数据异常或跨 session tick 溢出 → 应清理
        assertTrue(CooldownManager.isExpired(1000, 2000, 100));
    }

    @Test
    @DisplayName("isExpired handles max long values without overflow")
    void isExpired_maxLongValues() {
        // Long.MAX_VALUE 附近不应算术溢出
        assertFalse(CooldownManager.isExpired(Long.MAX_VALUE, Long.MAX_VALUE - 50, 100));
        assertTrue(CooldownManager.isExpired(Long.MAX_VALUE, Long.MAX_VALUE - 100, 100));
    }

    // ── 与 isOnCooldown 的逻辑一致性 ──

    @Test
    @DisplayName("isExpired is the logical inverse of isOnCooldown time check")
    void isExpired_inverseOfIsOnCooldown() {
        // isOnCooldown: now - lastUsed < cooldown → true (still cooling)
        // isExpired:    now - lastUsed >= cooldown → true (can trigger)
        // 因此: isExpired == !isOnCooldown (zero cooldown 除外)

        long now = 5000, lastUsed = 4950;
        int cd = 100;

        boolean onCooldown = (now - lastUsed < cd);  // 50 < 100 → true
        boolean expired = CooldownManager.isExpired(now, lastUsed, cd);  // → false

        assertNotEquals(onCooldown, expired);  // 互逆
    }
}
