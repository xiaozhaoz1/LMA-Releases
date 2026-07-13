package littlemaidmoreaction.littlemaidmoreaction.impl.action.control;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import littlemaidmoreaction.littlemaidmoreaction.engine.TickScheduler;

import java.util.List;
import java.util.Map;

import static littlemaidmoreaction.littlemaidmoreaction.engine.EngineUtils.parseInt;

@RuleAction
public final class WaitAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.IntParam("ticks", "等待刻数", 20)
    );
    @Override public String id() { return "wait"; }
    @Override public String displayName() { return "等待"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isAsync() { return true; }
    @Override public void execute(RuleContext ctx, Map<String, String> params) {
        ctx.maid().getPersistentData().putInt(TickScheduler.WAIT_KEY, parseInt(params.getOrDefault("ticks","20"), 20));
    }
}

