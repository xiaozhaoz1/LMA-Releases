// core/engine/CooldownManager.java
package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;

/**
 * 规则冷却管理器 — 从 RuleMatcher 提取的冷却逻辑。
 *
 * <p>冷却数据存储在女仆 PersistentData 中，键前缀: "lma_rule_"。
 * 时间基准使用 game tick（maid.level().getGameTime()），
 * 与旧 RuleMatcher 的 PersistentData 键完全兼容。
 */
public final class CooldownManager {

    /** 冷却持久化键前缀，与旧 RuleMatcher.COOLDOWN_PREFIX 兼容 */
    static final String COOLDOWN_KEY_PREFIX = "lma_rule_";

    /**
     * 检查规则是否处于冷却中。
     *
     * @param rule 规则定义
     * @param maid 女仆实体
     * @return true 表示冷却未过，应跳过此规则
     */
    public static boolean isOnCooldown(RuleDef rule, EntityMaid maid) {
        if (rule.cooldown() <= 0) return false;
        long lastUsed = maid.getPersistentData().getLong(COOLDOWN_KEY_PREFIX + rule.id());
        long now = maid.level().getGameTime();
        return now - lastUsed < rule.cooldown();
    }

    /**
     * 应用冷却 — 规则成功匹配并执行后调用。
     */
    public static void applyCooldown(RuleDef rule, EntityMaid maid) {
        if (rule.cooldown() > 0) {
            maid.getPersistentData().putLong(
                COOLDOWN_KEY_PREFIX + rule.id(),
                maid.level().getGameTime()
            );
        }
    }

    /**
     * 清除指定规则的冷却（调试/hot-reload 用）。
     */
    public static void clearCooldown(RuleDef rule, EntityMaid maid) {
        maid.getPersistentData().remove(COOLDOWN_KEY_PREFIX + rule.id());
    }

    /**
     * 纯时间比较 — 不依赖 Minecraft，可直接单元测试。
     *
     * @param now           当前 tick
     * @param lastUsed      上次触发 tick (0 = 从未触发)
     * @param cooldownTicks 冷却 tick 数
     * @return true 如果冷却已过期（可以再次触发）
     */
    static boolean isExpired(long now, long lastUsed, int cooldownTicks) {
        if (cooldownTicks <= 0) return true;
        if (lastUsed == 0) return true;  // 从未触发
        // 防溢出：lastUsed > now 说明 tick 溢出或数据异常
        if (lastUsed > now) return true;
        return now - lastUsed >= cooldownTicks;
    }

    private CooldownManager() {}
}
