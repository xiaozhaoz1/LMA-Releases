package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/** 吸血 — 造成伤害并治疗攻击者。参数: amount/ratio(吸血比例)/target */
@RuleAction
public final class LifeStealAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.DoubleParam("amount", "伤害量", 5.0),
        new TypedParam.DoubleParam("ratio", "吸血比例", 0.5),
        new TypedParam.SelectParam("target", "目标", "target",
            List.of("self", "target", "owner"))
    );
    @Override public String id() { return "life_steal"; }
    @Override public String displayName() { return "吸血"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        var t = p.resolveTarget(ctx.maid(), ctx.target());
        if (t == null || !t.isAlive()) return;
        CombatOutput.lifeSteal(t, ctx.maid(), (float) p.getDouble("amount"), (float) p.getDouble("ratio"));
    }
}
