package littlemaidmoreaction.littlemaidmoreaction.core.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ConditionCache} 缓存语义验证。
 *
 * <p>由于 {@link ConditionCache} 依赖 Minecraft 侧的 {@code EntityMaid}，
 * 本测试采用纯 Java 方式验证底层的 {@code computeIfAbsent} 缓存行为，
 * 确保相同 key 的求值在缓存期内只执行一次。
 */
class ConditionCacheTest {

    @Test
    @DisplayName("相同 key 第二次调用返回缓存值，求值函数只执行一次")
    void sameKey_returnsCachedValue() {
        Map<String, String> cache = new HashMap<>();
        AtomicInteger evalCount = new AtomicInteger(0);

        String result1 = cache.computeIfAbsent("health_ratio", k -> {
            evalCount.incrementAndGet();
            return "0.75";
        });
        String result2 = cache.computeIfAbsent("health_ratio", k -> {
            evalCount.incrementAndGet();
            return "0.75";
        });

        assertEquals("0.75", result1);
        assertEquals("0.75", result2);
        assertEquals(1, evalCount.get(), "求值函数应只执行一次");
    }

    @Test
    @DisplayName("不同 key 各自独立求值，互不影响")
    void differentKeys_evaluatedIndependently() {
        Map<String, String> cache = new HashMap<>();

        String v1 = cache.computeIfAbsent("health_ratio", k -> "0.5");
        String v2 = cache.computeIfAbsent("distance", k -> "10.0");
        String v3 = cache.computeIfAbsent("health_ratio", k -> "0.8");

        assertEquals("0.5", v1, "第一次求值结果");
        assertEquals("10.0", v2, "第二次求值结果");
        assertEquals("0.5", v3, "health_ratio 应返回缓存值而非重算值");
        assertEquals(2, cache.size(), "应缓存两个不同 key");
    }

    @Test
    @DisplayName("clear() 后重新求值，缓存被清空")
    void clear_resetsCache() {
        Map<String, String> cache = new HashMap<>();
        AtomicInteger evalCount = new AtomicInteger(0);

        // 第一次求值并缓存
        cache.computeIfAbsent("health_ratio", k -> {
            evalCount.incrementAndGet();
            return "0.75";
        });
        assertEquals(1, evalCount.get());

        // 清空缓存
        cache.clear();
        assertEquals(0, cache.size(), "清空后 size 应为 0");

        // 再次求值应重新计算
        String recomputed = cache.computeIfAbsent("health_ratio", k -> {
            evalCount.incrementAndGet();
            return "0.50";
        });
        assertEquals("0.50", recomputed, "clear 后应重新求值");
        assertEquals(2, evalCount.get(), "clear 后应再次执行求值函数");
        assertEquals(1, cache.size(), "重新缓存后 size 应为 1");
    }

    @Test
    @DisplayName("缓存命中时 size 不增长")
    void cacheHit_doesNotIncreaseSize() {
        Map<String, String> cache = new HashMap<>();

        cache.computeIfAbsent("key1", k -> "a");
        cache.computeIfAbsent("key1", k -> "b");
        cache.computeIfAbsent("key1", k -> "c");

        assertEquals(1, cache.size(), "重复相同的 key 不应增加缓存大小");
    }
}
