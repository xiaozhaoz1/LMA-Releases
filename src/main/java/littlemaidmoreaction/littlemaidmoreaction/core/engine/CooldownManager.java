// core/engine/CooldownManager.java
package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import littlemaidmoreaction.littlemaidmoreaction.core.model.RuleDef;

/**
 * 规则冷却管理器 — 零 Minecraft 依赖，纯数值操作。
 *
 * <p>冷却数据存储在 PersistentData 中，键前缀: "lma_rule_"。
 * 时间基准使用 game tick，调用方负责从 PersistentData 读取/写入。
 * isExpired() 可独立单元测试。
 */
public final class CooldownManager {

    /** 冷却持久化键前缀 */
    public static final String COOLDOWN_KEY_PREFIX = "lma_rule_";

    /**
     * 检查规则是否处于冷却中。
     *
     * @param rule       规则定义
     * @param lastUsed   上次触发 tick (从 PersistentData 读取, 0=从未)
     * @param gameTime   当前游戏 tick
     * @return true 表示冷却未过，应跳过此规则
     */
    public static boolean isOnCooldown(RuleDef rule, long lastUsed, long gameTime) {
        if (rule.cooldown() <= 0) return false;
        return !isExpired(gameTime, lastUsed, rule.cooldown());
    }

    /**
     * 计算应用冷却后的新 lastUsed 值（当前时间）。
     * 调用方负责写入 PersistentData。
     *
     * @return 当前 gameTime (即新的 lastUsed)
     */
    public static long newCooldownStamp(long gameTime) {
        return gameTime;
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
