package littlemaidmoreaction.littlemaidmoreaction.impl.action.maid;

import littlemaidmoreaction.littlemaidmoreaction.api.context.RuleContext;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.api.ParamExtractor;
import littlemaidmoreaction.littlemaidmoreaction.compat.vanilla.execute.crop.AutoCropHandler;
import littlemaidmoreaction.littlemaidmoreaction.core.annotation.RuleAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.ActionCategory;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.action.IAction;
import littlemaidmoreaction.littlemaidmoreaction.core.spi.param.TypedParam;

import java.util.List;
import java.util.Map;

/**
 * 自动作物匹配开关 — 委托给 {@link AutoCropHandler}。
 */
@RuleAction
public final class AutoMatchCropAction implements IAction {
    private static final List<TypedParam<?>> PARAMS = List.of(
            new TypedParam.SelectParam("scope", "作用范围", "maid",
                    List.of("maid", "global")),
            new TypedParam.BoolParam("enabled", "启用", true)
    );

    @Override public String id() { return "auto_match_crop"; }
    @Override public String displayName() { return "自动匹配种子"; }
    @Override public ActionCategory category() { return ActionCategory.MAID; }
    @Override public List<TypedParam<?>> params() { return PARAMS; }

    @Override
    public void execute(RuleContext ctx, Map<String, String> raw) {
        var p = ParamExtractor.from(raw, PARAMS);
        boolean enabled = p.getBool("enabled");
        if ("global".equals(p.getString("scope"))) AutoCropHandler.setGlobal(enabled);
        else AutoCropHandler.setMaid(ctx.maid(), enabled);
    }
}
