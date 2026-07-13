package littlemaidmoreaction.littlemaidmoreaction.impl.action.world;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.world.WorldOutput;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/** 女仆头顶气泡 — 委托给 {@link WorldOutput#sendBubble}. */
@RuleAction
public final class SendBubbleAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
        new TypedParam.StringParam("text", "文字", "主人~"),
        new TypedParam.IntParam("duration", "持续时间(tick)", 60)
    );

    @Override public String id() { return "send_bubble"; }
    @Override public String displayName() { return "女仆气泡"; }
    @Override public ActionCategory category() { return ActionCategory.MESSAGE; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public boolean isGameStateMutating() { return false; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        if (ctx.maid().level().isClientSide()) return;
        var p = ParamExtractor.from(raw, PARAMS);
        WorldOutput.sendBubble(ctx.maid(), p.getString("text"), p.getInt("duration"));
    }
}
