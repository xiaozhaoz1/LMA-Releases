package littlemaidmoreaction.littlemaidmoreaction.impl.action.control;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import java.util.List;
import java.util.Map;

/** 中断后续规则 — 执行后 RuleEngine 停止处理同事件其余候选规则。类似语言里的 if break。 */
@RuleAction
public final class BreakAction implements IAction {
    @Override public String id() { return "break"; }
    @Override public String displayName() { return "中断后续规则"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String, String> params) {
        ctx.setAttribute("_break", "true");
    }
}
