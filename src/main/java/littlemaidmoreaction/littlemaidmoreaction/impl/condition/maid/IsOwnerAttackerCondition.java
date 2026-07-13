package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
/**
 * 主人攻击者条件 — 检测伤害来源实体是否为女仆主人。
 * PersistentData 时间窗口：触发后20tick(1秒)内持续返回true。
 */
@RuleCondition
public final class IsOwnerAttackerCondition implements ICondition {
    @Override public String key() { return "is_owner_attacker"; }
    @Override public String displayName() { return "主人攻击者"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        long now = ctx.maid().level().getGameTime();
        var pd = ctx.maid().getPersistentData();

        long t = pd.getLong("lma_owner_attacker_tick");
        if (t > 0 && now - t <= 20 && t <= now) return "true";

        boolean isNow = ctx.source() != null
                && ctx.source().getEntity() instanceof LivingEntity a
                && ctx.maid().isOwnedBy(a);
        if (isNow) {
            pd.putLong("lma_owner_attacker_tick", now);
            return "true";
        }
        return "false";
    }
}

