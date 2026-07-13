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

@RuleAction
public final class DealDamageAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.DoubleParam("amount", "伤害量", 10.0),
        new TypedParam.SelectParam("damage_type", "伤害类型", "mob_attack",
            List.of("mob_attack", "magic", "generic", "execution_kill")),
        new TypedParam.BoolParam("ignore_armor", "无视护甲", false),
        new TypedParam.SelectParam("target", "目标", "target",
            List.of("self", "target", "owner"))
    );
    @Override public String id() { return "deal_damage"; }
    @Override public String displayName() { return "造成伤害"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        var t = p.resolveTarget(ctx.maid(), ctx.target());
        if (t == null || !t.isAlive()) return;
        String type = p.getString("damage_type");
        float dmg = (float) p.getDouble("amount");
        if (p.getBool("ignore_armor")) {
            CombatOutput.trueDamage(t, ctx.maid(), dmg);
        } else switch (type) {
            case "execution_kill" -> CombatOutput.executionKill(t, ctx.maid());
            case "magic"         -> CombatOutput.magicDamage(t, ctx.maid(), dmg);
            case "generic"       -> CombatOutput.genericDamage(t, ctx.maid(), dmg);
            default              -> CombatOutput.damage(t, ctx.maid(), dmg);
        }
    }
}
