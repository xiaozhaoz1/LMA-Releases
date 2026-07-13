package littlemaidmoreaction.littlemaidmoreaction.impl.action.movement;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.movement.MovementOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/**
 * 守卫当前位置 — 委托给 {@link MovementOutput#guardPos}.
 */
@RuleAction
public final class GuardPosAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("distance", "守卫范围", 16)
    );

    @Override public String id() { return "guard_pos"; }
    @Override public String displayName() { return "守卫位置"; }
    @Override public ActionCategory category() { return ActionCategory.MOVEMENT; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        MovementOutput.guardPos(ctx.maid(), ctx.maid().blockPosition(), p.getInt("distance"));
    }
}
