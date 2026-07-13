package littlemaidmoreaction.littlemaidmoreaction.impl.action.visual;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.anim.AnimExecute;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.Map;

/** 播放动画 — 委托给 {@link AnimExecute#execute}. */
@RuleAction
public final class PlayAnimAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
            new TypedParam.SelectParam("mode", "动画模式", "INSTANT", List.of("INSTANT", "FULL")),
            new TypedParam.StringParam("anim", "动画名", ""),
            new TypedParam.StringParam("anim_start", "开始动画", ""),
            new TypedParam.StringParam("anim_casting", "施法动画", ""),
            new TypedParam.StringParam("anim_end", "结束动画", ""),
            new TypedParam.StringParam("dur_start", "开始时长的tick", "20"),
            new TypedParam.StringParam("dur_casting", "施法时长的tick", "20"),
            new TypedParam.StringParam("dur_end", "结束时长的tick", "20"),
            new TypedParam.BoolParam("auto_wait", "动画完成后等待", true)
    );
    @Override public String id() { return "play_anim"; }
    @Override public String displayName() { return "播放动画"; }
    @Override public ActionCategory category() { return ActionCategory.VISUAL; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }
    @Override public void execute(RuleContext ctx, Map<String, String> raw) {
        if (!(ctx.maid().level() instanceof ServerLevel sl)) return;
        var p = ParamExtractor.from(raw, PARAMS);
        boolean ok = AnimExecute.execute(sl, ctx.maid(),
            p.getString("mode"), p.getString("anim"),
            p.getString("anim_start"), p.getString("anim_casting"), p.getString("anim_end"),
            p.getString("dur_start"), p.getString("dur_casting"), p.getString("dur_end"),
            p.getBool("auto_wait"));
        if (ok) {
            ctx.setAttribute("last_anim", p.getString("mode").equals("FULL")
                ? p.getString("anim_start") : p.getString("anim"));
            if (p.getBool("auto_wait")) {
                ctx.setAttribute("_auto_wait", "true");
                ctx.setAttribute("_ai_frozen", "true");
            }
        }
    }
}
