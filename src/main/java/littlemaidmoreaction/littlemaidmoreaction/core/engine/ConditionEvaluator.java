package littlemaidmoreaction.littlemaidmoreaction.core.engine;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionOperator;
import littlemaidmoreaction.littlemaidmoreaction.core.registry.ConditionRegistry;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import littlemaidmoreaction.littlemaidmoreaction.core.model.ConditionDef;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/**
 * 条件评估引擎 — key [math] op val [math] vs Minecraft 游戏状态。
 *
 * <p>v5: 条件解析委托给 {@link ConditionRegistry} 中注册的 {@link ICondition} 实现。
 * {@code data:<key>} 前缀动态条件从女仆 PersistentData 读取。</p>
 */
public final class ConditionEvaluator {

    public static boolean evaluate(ConditionDef cond, EntityMaid maid,
                                    LivingEntity target, DamageSource source) {
        return evaluate(cond, new RuleContext(maid, target, source));
    }

    public static boolean evaluate(ConditionDef cond, RuleContext ctx) {
        if (cond.isBoolean()) {
            return Boolean.parseBoolean(resolveKey(cond.key(), ctx, cond.params()));
        }
        String actual = resolveKey(cond.key(), ctx, cond.params());
        if (cond.hasKeyMath()) actual = ConditionMatcher.applyMath(actual, cond.keyMath(), cond.keyMathVal());
        String expected = cond.isKeyRef()
                ? littlemaidmoreaction.littlemaidmoreaction.core.expression.ExpressionResolver.resolve(cond.val(), ctx.maid(), ctx.target(), ctx.source())
                : cond.val();
        if (cond.hasValMath()) expected = ConditionMatcher.applyMath(expected, cond.valMath(), cond.valMathVal());
        return ConditionOperator.fromToken(cond.op()).test(actual, expected);
    }

    public static String resolveKey(String key, EntityMaid maid,
                                     LivingEntity target, DamageSource source) {
        return resolveKey(key, new RuleContext(maid, target, source), Map.of());
    }

    public static String resolveKey(String key, RuleContext ctx, Map<String, String> params) {
        if (key.startsWith("data:")) {
            String dataKey = key.substring(5);
            var pd = ctx.maid().getPersistentData();
            return pd.contains(dataKey) ? pd.get(dataKey).getAsString() : "0";
        }
        ICondition condition = ConditionRegistry.get(key);
        if (condition != null) return condition.evaluate(ctx, params);
        return "0";
    }

    private ConditionEvaluator() {}
}
