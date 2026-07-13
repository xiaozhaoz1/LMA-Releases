package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/** 护盾 — 给目标添加伤害吸收效果。参数: amount(护盾量)/duration(tick)/target */
@RuleAction
public final class ShieldAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("amount", "护盾量", 4),
        new TypedParam.IntParam("duration", "持续时间", 200),
        new TypedParam.SelectParam("target", "目标", "self",
            List.of("self", "target", "owner"))
    );
    @Override public String id() { return "shield"; }
    @Override public String displayName() { return "护盾"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        var t = p.resolveTarget(ctx.maid(), ctx.target());
        if (t == null) return;
        CombatOutput.shieldEffect(t, p.getInt("amount"), p.getInt("duration"));
    }
}
