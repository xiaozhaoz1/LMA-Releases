package littlemaidmoreaction.littlemaidmoreaction.engine;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.engine.ConditionEvaluator;
import littlemaidmoreaction.littlemaidmoreaction.core.expression.ExpressionResolver;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 引擎共享工具方法 — 供所有 executor 和 RuleEngine 使用的安全解析与目标解析。
 *
 * 设计要点
 * - 所有方法为 static，无状态，线程安全
 * - 数值解析静默吞 NumberFormatException，返回默认值（规则编辑器可能输入非数字字符串）
 * - NBT 解析吞所有异常并返回 null（编辑器可能写入非法 JSON 片段）
 */
public final class EngineUtils {

    // ---- 条件评估 + 表达式解析 MC 便利重载 (v35.1: 从 core/ 移入) ----

    /** MC 便利重载 → 委托 core/ConditionEvaluator */
    public static boolean evaluateCondition(ConditionDef cond, EntityMaid maid,
                                            LivingEntity target, DamageSource source) {
        return ConditionEvaluator.evaluate(cond, new RuleContext(maid, target, source));
    }

    /** MC 便利重载 → 委托 core/ConditionEvaluator */
    public static String resolveConditionKey(String key, EntityMaid maid,
                                             LivingEntity target, DamageSource source) {
        return ConditionEvaluator.resolveKey(key, new RuleContext(maid, target, source), java.util.Map.of());
    }

    /** MC 便利重载 → 委托 core/ExpressionResolver */
    public static String resolveExpression(String expr, EntityMaid maid,
                                           LivingEntity target, DamageSource source) {
        return ExpressionResolver.resolve(expr,
            key -> ConditionEvaluator.resolveKey(key, new RuleContext(maid, target, source), java.util.Map.of()));
    }

    private EngineUtils() {} // 工具类禁止实例化

    // ---- 数值安全解析 ----

    /** 安全解析 int，失败返回默认值 */
    public static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    /** 安全解析 double，失败返回默认值 */
    public static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    /** 安全解析 float，失败返回默认值 */
    public static float parseFloat(String s, float def) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return def; }
    }

    /** 安全解析 boolean，失败返回默认值 */
    public static boolean parseBoolean(String s, boolean def) {
        if (s == null || s.isEmpty()) return def;
        return switch (s.toLowerCase()) {
            case "true", "1", "yes" -> true;
            case "false", "0", "no" -> false;
            default -> def;
        };
    }

    /** 将 double 值钳制到 [min, max] 范围 */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---- NBT 安全解析 ----

    /**
     * 将 JSON 字符串解析为 CompoundTag，失败返回 null。
     * 适用于 SpawnEntity / GiveItem / DropItem 的 NBT 参数。
     */
    public static CompoundTag parseNbtSafe(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            Tag tag = TagParser.parseTag(json);
            return tag instanceof CompoundTag ct ? ct : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 目标解析 ----

    /**
     * 解析 target 参数为实际实体引用。
     *
     * @param maid   触发女仆（永非 null）
     * @param target 事件目标（可为 null）
     * @param mode   self / target / owner
     * @return 解析后的实体，target 模式且目标为 null 时返回 null
     */
    public static LivingEntity resolveTarget(EntityMaid maid, LivingEntity target, String mode) {
        return switch (mode) {
            case "self"   -> maid;
            case "target" -> target;
            case "owner"  -> {
                var uuid = maid.getOwnerUUID();
                yield uuid != null ? maid.level().getPlayerByUUID(uuid) : null;
            }
            default -> target;
        };
    }
}
