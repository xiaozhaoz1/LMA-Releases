package littlemaidmoreaction.littlemaidmoreaction.compat.ysm.impl.action;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.ysm.output.YsmWriter;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import java.util.List;
import java.util.Map;

/** 播放YSM轮盘 — 委托给 {@link YsmWriter#playRoulette}. */
@RuleAction
public final class PlayYsmRouletteAction implements IAction {
    @Override public String id() { return "play_ysm_roulette"; }
    @Override public String displayName() { return "播放YSM轮盘动画"; }
    @Override public ActionCategory category() { return ActionCategory.MAID_EXT; }
    @Override public List<TypedParam<?>> params() { return List.of(new TypedParam.StringParam("anim_name", "动画名称", "")); }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        String n = raw.getOrDefault("anim_name", ""); if (!n.isEmpty()) YsmWriter.playRoulette(ctx.maid(), n);
    }
}
