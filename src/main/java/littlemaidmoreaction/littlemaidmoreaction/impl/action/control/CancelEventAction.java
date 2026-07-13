package littlemaidmoreaction.littlemaidmoreaction.impl.action.control;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

@RuleAction
public final class CancelEventAction implements IAction {
    @Override public String id() { return "cancel_event"; }
    @Override public String displayName() { return "取消事件"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<TypedParam<?>> params() { return List.of(); }
    @Override public boolean cancelsEvent() { return true; }
    @Override public void execute(RuleContext ctx, Map<String, String> params) {}
}

