package littlemaidmoreaction.littlemaidmoreaction.impl.action.combat;

import littlemaidmoreaction.littlemaidmoreaction.LittleMaidMoreAction;
import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.vanilla.output.combat.CombatOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Map;

/** 强制女仆对目标执行一次完整的近战攻击（含好感度、饰品、横扫等全部修饰）。target 为 null 时跳过。 */
@RuleAction
public final class DoHurtTargetAction implements IAction {
    @Override public String id() { return "do_hurt_target"; }
    @Override public String displayName() { return "强制近战攻击"; }
    @Override public ActionCategory category() { return ActionCategory.COMBAT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String, String> params) {
        LivingEntity target = ctx.target();
        if (target == null) {
            LittleMaidMoreAction.LOGGER.warn("[DoHurtTarget] target is null, maid={}", ctx.maid().getId());
            return;
        }
        CombatOutput.doHurtTarget(ctx.maid(), target);
    }
}
