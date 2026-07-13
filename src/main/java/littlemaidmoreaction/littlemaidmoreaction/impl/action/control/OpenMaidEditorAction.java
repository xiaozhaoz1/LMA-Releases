package littlemaidmoreaction.littlemaidmoreaction.impl.action.control;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.network.OpenMaidEditorMessage;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/** 打开独立规则编辑器 — 向交互玩家发送网络包 */
@RuleAction
public final class OpenMaidEditorAction implements IAction {
    @Override public String id() { return "open_maid_editor"; }
    @Override public String displayName() { return "打开独立编辑规则"; }
    @Override public ActionCategory category() { return ActionCategory.CONTROL; }
    @Override public List<littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam<?>> params() { return List.of(); }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        if (ctx.target() instanceof ServerPlayer sp) OpenMaidEditorMessage.sendToPlayer(ctx.maid(), sp);
    }
}
