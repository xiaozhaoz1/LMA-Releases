package littlemaidmoreaction.littlemaidmoreaction.impl.action.movement;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.output.maid.MaidStateWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;

import java.util.List;
import java.util.Map;

/**
 * 跟随主人 (v11 P5) — 解除家园模式让女仆跟随。
 *
 * <p>关闭 homeMode 使女仆切换到跟随模式。
 * 搭配 guard_pos/set_home_mode 可切换跟随/守卫状态。
 */
@RuleAction
public final class FollowOwnerAction implements IAction {

    @Override public String id() { return "follow_owner"; }
    @Override public String displayName() { return "跟随主人"; }
    @Override public ActionCategory category() { return ActionCategory.MOVEMENT; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public boolean isGameStateMutating() { return true; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> rawParams) {
        MaidStateWriter.setHomeMode(ctx.maid(), false);
    }
}
