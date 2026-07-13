package littlemaidmoreaction.littlemaidmoreaction.impl.condition.maid;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleCondition;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ConditionValueType;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.condition.ICondition;
import java.util.Map;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
/**
 * 主手攻击条件 — 修复反转逻辑 + 武器检测 + 20tick时间窗口。
 * <ul>
 * <li>DamageSource.getEntity() != null → 有实体造成伤害（近战/弹射物来源）</li>
 * <li>maid主手持剑/斧 → 覆盖 {@code maid_hurt_target_pre} 事件(source=null)</li>
 * <li>PersistentData 时间窗口：触发后20tick(1秒)内持续为true</li>
 * </ul>
 */
@RuleCondition
public final class IsMainhandAttackCondition implements ICondition {
    @Override public String key() { return "is_mainhand_attack"; }
    @Override public String displayName() { return "主手攻击"; }
    @Override public ConditionCategory category() { return ConditionCategory.MAID; }
    @Override public ConditionValueType valueType() { return ConditionValueType.BOOL; }
    @Override
    public String evaluate(RuleContext ctx, Map<String, String> rawParams) {
        long now = ctx.maid().level().getGameTime();
        var pd = ctx.maid().getPersistentData();

        // 时间窗口：上次触发20tick内仍返回true
        long t = pd.getLong("lma_mainhand_attack_tick");
        if (t > 0 && now - t <= 20 && t <= now) return "true";

        // 检测1: source有实体来源 → 非环境伤害（近战/弹射物）
        boolean hasSourceEntity = ctx.source() != null && ctx.source().getEntity() != null;
        // 检测2: maid主手持剑/斧 → 覆盖maid_hurt_target_pre(sourcenull)
        boolean maidMelee = ctx.maid().getMainHandItem().getItem() instanceof SwordItem
                         || ctx.maid().getMainHandItem().getItem() instanceof AxeItem;

        if (hasSourceEntity || maidMelee) {
            pd.putLong("lma_mainhand_attack_tick", now);
            return "true";
        }
        return "false";
    }
}

