package littlemaidmoreaction.littlemaidmoreaction.api.context;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则执行上下文 — immutable core + mutable attributes + search cache。
 *
 * <p>核心字段（maid/target/source）不可变。
 * attributes 为可变 Map，用于规则执行期间的临时状态传递。
 * searchCache 为 condition→action 搜索复用缓存 (v12 P2)。
 *
 * <p>maid 永非 null（构造时检查），target/source 可 null。
 */
public final class RuleContext {
    private final EntityMaid maid;
    @Nullable private final LivingEntity target;
    @Nullable private final DamageSource source;
    private final Map<String, String> attributes;
    /** ★ v12 P2: 搜索缓存 (condition→action 结果复用)。 */
    private final Map<String, Object> searchCache = new ConcurrentHashMap<>();

    public RuleContext(EntityMaid maid, @Nullable LivingEntity target,
                       @Nullable DamageSource source, Map<String, String> attributes) {
        this.maid = Objects.requireNonNull(maid, "maid must not be null");
        this.target = target;
        this.source = source;
        this.attributes = attributes;
    }

    public RuleContext(EntityMaid maid, @Nullable LivingEntity target,
                       @Nullable DamageSource source) {
        this(maid, target, source, new ConcurrentHashMap<>());
    }

    public RuleContext(EntityMaid maid) {
        this(maid, null, null);
    }

    public RuleContext(EntityMaid maid, @Nullable LivingEntity target) {
        this(maid, target, null);
    }

    // ── 访问器 ──

    public EntityMaid maid() { return maid; }

    @Nullable
    public LivingEntity target() { return target; }

    @Nullable
    public DamageSource source() { return source; }

    public Map<String, String> attributes() { return attributes; }

    // ── 属性操作 ──

    /** 设置临时属性（脚本返回值、流程控制信号等） */
    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    /** 获取临时属性 */
    @Nullable
    public String getAttribute(String key) {
        return attributes.get(key);
    }

    /** 获取临时属性（带默认值） */
    public String getAttribute(String key, String defaultValue) {
        return attributes.getOrDefault(key, defaultValue);
    }

    // ── v12 P2: 搜索缓存 (condition → action 结果复用) ──

    /** 存入搜索缓存 (类型安全由调用方保证)。通常 key 使用 block_id 或 entity_type。 */
    public void putSearchCache(String key, Object value) {
        searchCache.put(key, value);
    }

    /** 获取搜索缓存。无缓存时返回 null。 */
    @SuppressWarnings("unchecked")
    public <T> T getSearchCache(String key) {
        return (T) searchCache.get(key);
    }

    /** 是否存在指定 key 的搜索缓存 */
    public boolean hasSearchCache(String key) {
        return searchCache.containsKey(key);
    }

    // ── 标准方法 ──
    // ★ v12 P2: 从 record 转为 class 以支持 searchCache。
    // equals/hashCode 仅比较 maid/target/source (attributes 和 searchCache 被排除)。
    // 旧 record 版的 equals 包含 attributes，但因每次 new ConcurrentHashMap 永不相等，
    // 实际行为无差异 — compareByValue 从未用于 RuleContext。

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuleContext that)) return false;
        return maid.equals(that.maid)
            && Objects.equals(target, that.target)
            && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maid, target, source);
    }

    @Override
    public String toString() {
        return "RuleContext[maid=" + maid + ", target=" + target + ", source=" + source + "]";
    }
}
