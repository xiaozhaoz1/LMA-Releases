package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
/**
 * 主人被攻击条件 — 检测主人是否为事件目标 或 主人最近受伤(hurtTime>0)。
 * PersistentData 时间窗口：触发后20tick(1秒)内持续返回true。
 */
@RuleCondition
public final class IsOwnerTargetCondition implements ICondition {
    @Override public String key() { return "is_owner_target"; }
    @Override public String displayName() { return "主人被攻击"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        long now = ctx.maid().level().getGameTime();
        var pd = ctx.maid().getPersistentData();

        // 时间窗口
        long t = pd.getLong("lma_owner_target_tick");
        if (t > 0 && now - t <= 20 && t <= now) return "true";

        // 检测1: 事件目标是否为女仆主人
        boolean isTarget = ctx.target() != null && ctx.maid().isOwnedBy(ctx.target());
        // 检测2: 主人最近受伤 (vanilla hurtTime=10tick窗口)
        var owner = ctx.maid().getOwner();
        boolean ownerHurt = owner instanceof LivingEntity && ((LivingEntity) owner).hurtTime > 0;

        if (isTarget || ownerHurt) {
            pd.putLong("lma_owner_target_tick", now);
            return "true";
        }
        return "false";
    }
}

